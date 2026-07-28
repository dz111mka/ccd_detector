package by.spectrometer.ui;

import by.spectrometer.model.Peak;
import by.spectrometer.model.SpectrumData;
import by.spectrometer.service.LogService;
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
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

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
    private final double[] xValues = new double[PIXEL_COUNT];
    private final List<PlotMarker> plotMarkers = new ArrayList<>();
    private final List<IndicatorSeries> indicatorSeries = new ArrayList<>();

    private boolean showAbsorbance = false;
    private boolean frozen = false;
    private boolean transmissionMode = false;
    private boolean wavelengthCalibrated = false;
    private Consumer<Integer> calibrationPixelSelectionHandler;

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

    public void setOnCalibrationPixelSelected(Consumer<Integer> handler) {
        this.calibrationPixelSelectionHandler = handler;
    }

    public void selectCalibrationPixel(int pixel) {
        if (calibrationPixelSelectionHandler != null) {
            calibrationPixelSelectionHandler.accept(pixel);
        }
    }

    public enum MarkerType {
        MINIMUM,
        PEAK
    }

    private record PlotMarker(MarkerType type, int pixel, double yValue, Node node) {
    }

    private record IndicatorSeries(MarkerType type, Series<Number, Number> series, int pixel, String color) {
    }

    @Override
    protected void layoutPlotChildren() {
        super.layoutPlotChildren();
        layoutOverlayMarkers();
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
            xValues[pixel] = pixel;
            spectrumPoints.add(new Data<>(xValues[pixel], 0.0));
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

        String tooltipText = String.format("%s: %.2f%s\nY: %.2f",
                getXAxisDisplayName(), xValue, getXAxisUnitSuffix(), yValue);
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
            updateXValues(data);
            spectrumSeries.getData().clear();
            ObservableList<XYChart.Data<Number, Number>> newPoints = FXCollections.observableArrayList();

            for (int i = 0; i < PIXEL_COUNT; i++) {
                double y = computeYValue(data, i, useAbs);
                // НЕ умножаем на 100 здесь - ось сама покажет проценты
                newPoints.add(new XYChart.Data<>(getXValueForPixel(i), y));
            }

            spectrumSeries.setData(newPoints);

            NumberAxis yAxis = (NumberAxis) getYAxis();
            if (transmissionMode) {
                yAxis.setLabel("Transmittance");
                yAxis.setAutoRanging(false);
                yAxis.setLowerBound(0);
                yAxis.setUpperBound(1.0);  // Диапазон 0-1 (0%-100%)
                // Форматируем как проценты
                yAxis.setTickLabelFormatter(new NumberAxis.DefaultFormatter(yAxis) {
                    @Override
                    public String toString(Number object) {
                        return String.format("%.0f%%", object.doubleValue() * 100);
                    }
                });
            } else {
                yAxis.setLabel("Intensity");
                yAxis.setAutoRanging(true);
                yAxis.setTickLabelFormatter(null);
            }
        });
    }

    private double computeYValue(SpectrumData data, int idx, boolean useAbsorbance) {
        if (useAbsorbance) {
            double denom = data.dark[idx] - data.reference[idx]; // Инвертировано!
            double value = denom > 50
                    ? -Math.log10((data.dark[idx] - data.raw[idx]) / denom)  // Инвертировано!
                    : 0.0;
            return value;
        }

        // Режим TRANSMISSION
        if (transmissionMode && data.darkBufferReady && data.referenceBufferReady) {
            double dark = data.dark[idx];
            double reference = data.reference[idx];
            double signal = data.raw[idx];
            double denominator = dark - reference;

            if (denominator > 50) {
                double transmittance = (dark - signal) / denominator;
                return Math.max(0, Math.min(1, transmittance));
            }
            return 0.0;
        }

        // Режим INTENSITY
        return Math.abs(4095 - data.raw[idx]);
    }

    public void setTransmissionMode(boolean enabled) {
        this.transmissionMode = enabled;
        NumberAxis yAxis = (NumberAxis) getYAxis();

        if (enabled) {
            yAxis.setLabel("Transmittance");
            yAxis.setAutoRanging(false);
            yAxis.setLowerBound(0);
            yAxis.setUpperBound(1.0);
            yAxis.setTickLabelFormatter(new NumberAxis.DefaultFormatter(yAxis) {
                @Override
                public String toString(Number object) {
                    return String.format("%.0f%%", object.doubleValue() * 100);
                }
            });
        } else {
            yAxis.setLabel("Intensity");  // Просто Intensity, без "inverted"
            yAxis.setAutoRanging(true);
            yAxis.setTickLabelFormatter(null);
        }
    }

    public void forceRedraw(SpectrumData data) {
        boolean useAbs = showAbsorbance && data.hasDark && data.hasRef;

        Platform.runLater(() -> {
            updateXValues(data);
            ObservableList<XYChart.Data<Number, Number>> newPoints = FXCollections.observableArrayList();

            for (int i = 0; i < PIXEL_COUNT; i++) {
                double y = computeYValue(data, i, useAbs);
                // НЕ умножаем на 100
                newPoints.add(new XYChart.Data<>(getXValueForPixel(i), y));
            }

            spectrumSeries.setData(newPoints);
        });
    }

    public void capture(SpectrumData data) {
        frozen = true;
        double[] capturedData = new double[PIXEL_COUNT];
        updateXValues(data);
        dataProcessor.clearMinima();
        for (int i = 0; i < PIXEL_COUNT; i++) {
            double y = computeYValue(data, i, false);
            capturedData[i] = y;  // Сохраняем как есть (0-1 для transmission, raw для intensity)
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
                // НЕ умножаем на 100 - данные уже в правильном диапазоне
                pts.add(new XYChart.Data<>(getXValueForPixel(i), y[i]));
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

    public void setGridVisible(boolean visible) {
        Platform.runLater(() -> {
            String visibility = visible ? "" : "-fx-stroke: transparent;";
            lookupAll(".chart-horizontal-grid-lines").forEach(node -> node.setStyle(visibility));
            lookupAll(".chart-vertical-grid-lines").forEach(node -> node.setStyle(visibility));
        });
    }

    public void clearOverlayMarkers(MarkerType type) {
        plotMarkers.removeIf(marker -> {
            if (marker.type() == type) {
                getPlotChildren().remove(marker.node());
                return true;
            }
            return false;
        });
    }

    public void addOverlayMarker(MarkerType type, int pixel, double yValue, Node node) {
        plotMarkers.add(new PlotMarker(type, pixel, yValue, node));
        getPlotChildren().add(node);
        layoutOverlayMarker(plotMarkers.getLast());
        node.toFront();
    }

    public void clearIndicatorLines(MarkerType type) {
        Iterator<IndicatorSeries> iterator = indicatorSeries.iterator();
        while (iterator.hasNext()) {
            IndicatorSeries indicator = iterator.next();
            if (indicator.type() == type) {
                getData().remove(indicator.series());
                iterator.remove();
            }
        }
    }

    public void addIndicatorLine(MarkerType type, int pixel, double yValue, String color) {
        double x = getXValueForPixel(pixel);
        double baseY = ((NumberAxis) getYAxis()).getLowerBound();
        double topY = yValue;

        Series<Number, Number> series = new Series<>();
        series.setName(type == MarkerType.MINIMUM ? "Minimum marker" : "Peak marker");
        series.getData().add(new XYChart.Data<>(x, baseY));
        series.getData().add(new XYChart.Data<>(x, topY));

        getData().add(series);
        indicatorSeries.add(new IndicatorSeries(type, series, pixel, color));

        Platform.runLater(() -> styleIndicatorSeries(series, color));
    }

    public double getXValueForPixel(int pixel) {
        if (pixel < 0 || pixel >= xValues.length) {
            return pixel;
        }
        return xValues[pixel];
    }

    public void refreshXAxis(SpectrumData data) {
        updateXValues(data);
        if (frozen && dataProcessor.getCapturedY() != null) {
            redrawFromArray(dataProcessor.getCapturedY());
        } else {
            forceRedraw(data);
        }
    }

    public String formatXValueForPixel(int pixel) {
        double value = getXValueForPixel(pixel);
        if (wavelengthCalibrated) {
            return String.format("%.2f nm (pixel %d)", value, pixel);
        }
        return String.format("pixel %d", pixel);
    }

    public boolean isWavelengthCalibrated() {
        return wavelengthCalibrated;
    }

    private void updateXValues(SpectrumData data) {
        wavelengthCalibrated = data.wavelengthCalibrated
                && data.wavelength[PIXEL_COUNT - 1] > data.wavelength[0];

        for (int i = 0; i < PIXEL_COUNT; i++) {
            xValues[i] = wavelengthCalibrated ? data.wavelength[i] : i;
        }

        NumberAxis xAxis = (NumberAxis) getXAxis();
        xAxis.setLabel(wavelengthCalibrated ? "Wavelength (nm)" : "Pixel");
        xAxis.setTickUnit(wavelengthCalibrated ? 250 : 500);
        if (!zoomManager.isZoomMode()) {
            xAxis.setLowerBound(xValues[0]);
            xAxis.setUpperBound(xValues[PIXEL_COUNT - 1]);
        }

        refreshIndicatorLinePositions();
    }

    private String getXAxisDisplayName() {
        return wavelengthCalibrated ? "Wavelength" : "Pixel";
    }

    private String getXAxisUnitSuffix() {
        return wavelengthCalibrated ? " nm" : "";
    }

    private void layoutOverlayMarkers() {
        plotMarkers.forEach(this::layoutOverlayMarker);
    }

    private void layoutOverlayMarker(PlotMarker marker) {
        double x = getXAxis().getDisplayPosition(getXValueForPixel(marker.pixel()));
        double y = getYAxis().getDisplayPosition(marker.yValue());
        double size = 12;
        boolean visible = x >= -size && y >= -size
                && x <= getWidth() + size && y <= getHeight() + size;

        marker.node().setVisible(visible);
        marker.node().relocate(x - size / 2, y - size / 2);
        marker.node().toFront();
    }

    private void refreshIndicatorLinePositions() {
        for (IndicatorSeries indicator : indicatorSeries) {
            double x = getXValueForPixel(indicator.pixel());
            if (indicator.series().getData().size() >= 2) {
                indicator.series().getData().get(0).setXValue(x);
                indicator.series().getData().get(1).setXValue(x);
            }
        }
    }

    private void styleIndicatorSeries(Series<Number, Number> series, String color) {
        Node line = series.getNode() == null ? null : series.getNode().lookup(".chart-series-line");
        if (line != null) {
            line.setStyle("-fx-stroke: " + color + "; -fx-stroke-width: 2.5px;");
            line.toFront();
        }

        Set<Node> symbols = series.getNode() == null ? Set.of() : series.getNode().lookupAll(".chart-line-symbol");
        for (Node symbol : symbols) {
            symbol.setVisible(false);
            symbol.setManaged(false);
        }
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
