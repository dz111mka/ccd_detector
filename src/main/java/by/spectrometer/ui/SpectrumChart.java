package by.spectrometer.ui;

import by.spectrometer.model.ChartScale;
import by.spectrometer.model.SpectrumData;
import by.spectrometer.model.Peak;
import by.spectrometer.ui.manager.ChartDataProcessor;
import by.spectrometer.ui.manager.ChartPeakController;
import by.spectrometer.ui.manager.ChartThemeManager;
import by.spectrometer.ui.manager.ChartZoomManager;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Point2D;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.chart.*;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

import java.util.*;

public class SpectrumChart extends LineChart<Number, Number> {

    // ────────────────────────────────────────────────────────────────
    // Constants and fields
    // ────────────────────────────────────────────────────────────────
    private static final int PIXEL_COUNT = 3648;

    private final ObservableList<Data<Number, Number>> spectrumPoints =
            FXCollections.observableArrayList();

    private final Series<Number, Number> spectrumSeries = new Series<>();
    private final Series<Number, Number> minimaSeries = new Series<>();
    private final Series<Number, Number> peaksSeries = new Series<>();
    private final Series<Number, Number> baselineSeries = new Series<>();

    private boolean showAbsorbance = false;
    private boolean frozen = false;

    // ────────────────────────────────────────────────────────────────
    // Managers
    // ────────────────────────────────────────────────────────────────
    private final ChartZoomManager zoomManager;
    private final ChartDataProcessor dataProcessor;
    private final ChartPeakController peakController;
    private final ChartThemeManager themeManager;

    // Tooltip для отображения координат при наведении
    private final Tooltip coordinateTooltip = new Tooltip();

    private Node plotArea;

    // ────────────────────────────────────────────────────────────────
    // Конструктор
    // ────────────────────────────────────────────────────────────────
    public SpectrumChart(SpectrumData initialData) {
        super(new NumberAxis(), new NumberAxis());

        this.zoomManager = new ChartZoomManager(this);
        this.dataProcessor = new ChartDataProcessor(this);
        this.peakController = new ChartPeakController(this);
        this.themeManager = new ChartThemeManager(this);

        initializeAxes();
        initializeSeries();
        redraw(initialData);
        initializeMouseHandling();
    }

    // ────────────────────────────────────────────────────────────────
    // Public getters (for managers)
    // ────────────────────────────────────────────────────────────────
    public Series<Number, Number> getSpectrumSeries() {
        return spectrumSeries;
    }

    public Series<Number, Number> getMinimaSeries() {
        return minimaSeries;
    }

    public Series<Number, Number> getPeaksSeries() {
        return peaksSeries;
    }

    public Series<Number, Number> getBaselineSeries() {
        return baselineSeries;
    }

    public double[] getCapturedY() {
        return dataProcessor.getCapturedY();
    }

    public boolean isFrozen() {
        return frozen;
    }

    public boolean isZoomMode() {
        return zoomManager.isZoomMode();
    }

    // ────────────────────────────────────────────────────────────────
    // Инициализация осей
    // ────────────────────────────────────────────────────────────────
    private void initializeAxes() {
        NumberAxis xAxis = (NumberAxis) getXAxis();
        xAxis.setLabel("Pixel");
        xAxis.setAutoRanging(false);
        xAxis.setLowerBound(0);
        xAxis.setUpperBound(PIXEL_COUNT - 1);
        xAxis.setTickUnit(500);
        xAxis.setMinorTickVisible(true);
        xAxis.setAnimated(false);
        xAxis.setForceZeroInRange(false);

        NumberAxis yAxis = (NumberAxis) getYAxis();
        yAxis.setLabel("Raw intensity");
        yAxis.setAutoRanging(true);
        yAxis.setAnimated(false);
    }

    // ────────────────────────────────────────────────────────────────
    // Инициализация серии и точек
    // ────────────────────────────────────────────────────────────────
    private void initializeSeries() {
        spectrumSeries.setName("Spectrum");
        getData().add(spectrumSeries);

        for (int pixel = 0; pixel < PIXEL_COUNT; pixel++) {
            spectrumPoints.add(new Data<>(pixel, 0.0));
        }

        spectrumSeries.setData(spectrumPoints);

        setCreateSymbols(false);
        setAnimated(false);

        minimaSeries.setName("Minima");
        getData().add(minimaSeries);

        peaksSeries.setName("Peaks");
        getData().add(peaksSeries);

        baselineSeries.setName("Baseline");
        getData().add(baselineSeries);

        applyMinimaSeriesStyle();
        applyPeaksSeriesStyle();
        applyBaselineSeriesStyle();
    }

    // ────────────────────────────────────────────────────────────────
    // Initialization of mouse handling (tooltip)
    // ────────────────────────────────────────────────────────────────
    private void initializeMouseHandling() {
        Platform.runLater(() -> {
            plotArea = lookup(".chart-plot-background");
            if (plotArea != null) {
                plotArea.setOnMouseMoved(this::onMouseMove);
                plotArea.setOnMouseEntered(this::onMouseEnter);
                plotArea.setOnMouseExited(this::onMouseExit);
            }
        });
    }

    private void onMouseEnter(MouseEvent e) {
        if (!zoomManager.isZoomMode()) {
            plotArea.setCursor(Cursor.CROSSHAIR);
        }
    }

    private void onMouseExit(MouseEvent e) {
        if (!zoomManager.isZoomMode()) {
            plotArea.setCursor(Cursor.DEFAULT);
        }
        coordinateTooltip.hide();
    }

    private void onMouseMove(MouseEvent e) {
        Point2D p = toPlotArea(e);
        NumberAxis xAxis = (NumberAxis) getXAxis();
        NumberAxis yAxis = (NumberAxis) getYAxis();

        double xValue = xAxis.getValueForDisplay(p.getX()).doubleValue();
        double yValue = yAxis.getValueForDisplay(p.getY()).doubleValue();

        String tooltipText = String.format("X: %.2f\nY: %.2f", xValue, yValue);
        coordinateTooltip.setText(tooltipText);

        coordinateTooltip.show(this, e.getScreenX() + 10, e.getScreenY() + 10);
    }

    private Point2D toPlotArea(MouseEvent e) {
        return plotArea.sceneToLocal(e.getSceneX(), e.getSceneY());
    }

    // ────────────────────────────────────────────────────────────────
    // Обновление данных
    // ────────────────────────────────────────────────────────────────
    public void redraw(SpectrumData data) {
        boolean useAbs = showAbsorbance && data.hasDark && data.hasRef;

        Platform.runLater(() -> {
            if (frozen && dataProcessor.getCapturedY() != null) {
                return;
            }

            spectrumSeries.getData().clear();

            ObservableList<XYChart.Data<Number, Number>> newPoints = FXCollections.observableArrayList();

            for (int i = 0; i < PIXEL_COUNT; i++) {
                double y = computeYValue(data, i, useAbs);
                newPoints.add(new XYChart.Data<>(i, y));
            }

            spectrumSeries.setData(newPoints);

            double maxY = newPoints.stream()
                    .mapToDouble(p -> p.getYValue().doubleValue())
                    .max().orElse(40960.0);

            NumberAxis yAxis = (NumberAxis) getYAxis();
            yAxis.setUpperBound(Math.max(40960, maxY * 1.1));
            yAxis.setLowerBound(0);
        });
    }

    private double computeYValue(SpectrumData data, int idx, boolean useAbsorbance) {
        double rawValue;

        if (useAbsorbance) {
            double denom = data.reference[idx] - data.dark[idx];
            rawValue = denom > 50
                    ? -Math.log10((data.raw[idx] - data.dark[idx]) / denom)
                    : 0.0;
        } else {
            rawValue = data.raw[idx];
        }
        return (4095 - rawValue) * 10;
    }

    public void capture(SpectrumData data) {
        frozen = true;
        double[] capturedData = new double[PIXEL_COUNT];
        dataProcessor.clearMinima();
        for (int i = 0; i < PIXEL_COUNT; i++) {
            capturedData[i] = computeYValue(data, i, false);
        }
        dataProcessor.setCapturedY(capturedData);
        redrawFromArray(capturedData);
    }

    public void release() {
        frozen = false;
        dataProcessor.clearMinima();
    }

    private void redrawFromArray(double[] y) {
        Platform.runLater(() -> {
            spectrumSeries.getData().clear();
            ObservableList<XYChart.Data<Number, Number>> pts = FXCollections.observableArrayList();
            for (int i = 0; i < PIXEL_COUNT; i++) {
                pts.add(new XYChart.Data<>(i, y[i]));
            }
            spectrumSeries.setData(pts);

            if (dataProcessor.hasMinima() && !dataProcessor.getMinima().isEmpty()) {
                dataProcessor.drawMinimaMarkers();
            }
        });
    }

    // ────────────────────────────────────────────────────────────────
    // Delegated methods to managers
    // ────────────────────────────────────────────────────────────────
    public List<Integer> findLocalMinima(int window, double threshold) {
        return dataProcessor.findLocalMinima(window, threshold);
    }

    public void clearMinima() {
        dataProcessor.clearMinima();
    }

    public void smooth(int window) {
        dataProcessor.smooth(window);
    }

    public void setZoomMode(boolean enabled) {
        zoomManager.setZoomMode(enabled);
    }

    public void zoomBack() {
        zoomManager.zoomBack();
    }

    public void zoomForward() {
        zoomManager.zoomForward();
    }

    public List<Peak> detectPeaks(double threshold, int window) {
        return peakController.detectPeaks(threshold, window);
    }

    public void clearPeaks() {
        peakController.clearPeaks();
    }

    public List<Peak> getPeaks() {
        return peakController.getPeaks();
    }

    public double[] getBaseline() {
        return peakController.getBaseline();
    }

    public void applyTheme(boolean isDarkTheme) {
        themeManager.applyTheme(isDarkTheme);
    }

    // ────────────────────────────────────────────────────────────────
    // Style application
    // ────────────────────────────────────────────────────────────────
    private void applyMinimaSeriesStyle() {
        minimaSeries.getNode().setStyle("-fx-background-color: transparent;");

        minimaSeries.nodeProperty().addListener((obs, oldNode, newNode) -> {
            if (newNode != null) {
                Platform.runLater(() -> {
                    Node line = newNode.lookup(".chart-series-line");
                    if (line != null) {
                        line.setStyle("-fx-stroke: transparent;");
                    }

                    Set<Node> symbols = newNode.lookupAll(".chart-series-symbol");
                    for (Node symbol : symbols) {
                        symbol.setStyle("""
                            -fx-background-color: red;
                            -fx-background-radius: 5px;
                            -fx-padding: 5px;
                            -fx-border-color: white;
                            -fx-border-width: 2px;
                            -fx-border-radius: 5px;
                            -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.8), 5, 0, 0, 0);
                        """);
                        symbol.setVisible(true);
                    }
                });
            }
        });
    }

    private void applyPeaksSeriesStyle() {
        peaksSeries.getNode().setStyle("-fx-background-color: transparent;");

        peaksSeries.nodeProperty().addListener((obs, oldNode, newNode) -> {
            if (newNode != null) {
                Platform.runLater(() -> {
                    Node line = newNode.lookup(".chart-series-line");
                    if (line != null) {
                        line.setStyle("-fx-stroke: transparent;");
                    }

                    Set<Node> symbols = newNode.lookupAll(".chart-series-symbol");
                    for (Node symbol : symbols) {
                        symbol.setStyle("""
                            -fx-background-color: green;
                            -fx-background-radius: 5px;
                            -fx-padding: 5px;
                            -fx-border-color: white;
                            -fx-border-width: 2px;
                            -fx-border-radius: 5px;
                            -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.8), 5, 0, 0, 0);
                        """);
                        symbol.setVisible(true);
                    }
                });
            }
        });
    }

    private void applyBaselineSeriesStyle() {
        baselineSeries.getNode().setStyle("-fx-stroke: orange; -fx-stroke-width: 2px;");
    }

    // ────────────────────────────────────────────────────────────────
    // Helper methods
    // ────────────────────────────────────────────────────────────────
    public void addPlotChild(Node node) {
        getPlotChildren().add(node);
    }

    // Геттер для отладки
    public int getMinimaCount() {
        return dataProcessor.getMinima().size();
    }
}