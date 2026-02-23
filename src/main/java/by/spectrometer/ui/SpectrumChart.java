package by.spectrometer.ui;

import by.spectrometer.model.ChartScale;
import by.spectrometer.model.SpectrumData;
import by.spectrometer.model.Peak;
import by.spectrometer.service.PeakDetectionService;
import by.spectrometer.service.PeakFittingService;
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
    // Константы и поля
    // ────────────────────────────────────────────────────────────────
    private static final int PIXEL_COUNT = 3648;

    private final ObservableList<Data<Number, Number>> spectrumPoints =
            FXCollections.observableArrayList();

    private final Series<Number, Number> spectrumSeries = new Series<>();
    private final Series<Number, Number> minimaSeries = new Series<>();
    private final Series<Number, Number> peaksSeries = new Series<>();
    private final Series<Number, Number> baselineSeries = new Series<>();
    private final List<Integer> minima = new ArrayList<>();
    private final List<Peak> peaks = new ArrayList<>();
    private double[] baseline = null;
    
    private final PeakDetectionService peakDetectionService = new PeakDetectionService();
    private final PeakFittingService peakFittingService = new PeakFittingService();

    private boolean showAbsorbance = false;
    private boolean frozen = false;
    private double[] capturedY = null;

    // Флаг, показывающий, были ли найдены минимумы
    private boolean hasMinima = false;

    // Зум
    private boolean zoomMode = false;
    private final Deque<ChartScale> backHistory = new ArrayDeque<>();
    private final Deque<ChartScale> forwardHistory = new ArrayDeque<>();

    private Rectangle zoomRect;
    private double dragStartX;
    private double dragStartY;

    private Node plotArea;


    // ────────────────────────────────────────────────────────────────
    // Конструктор
    // ────────────────────────────────────────────────────────────────
    public SpectrumChart(SpectrumData initialData) {
        super(new NumberAxis(), new NumberAxis());

        initializeAxes();
        initializeSeries();
        redraw(initialData);
        initializeZoom();
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
        yAxis.setAutoRanging(true);  // можно потом переключить на false + 0..4096
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

        // ВАЖНО: Для всего графика отключаем символы
        setCreateSymbols(false);
        setAnimated(false);

        minimaSeries.setName("Minima");
        // НО для серии минимумов нужно включить символы!
        getData().add(minimaSeries);

        peaksSeries.setName("Peaks");
        getData().add(peaksSeries);
        
        baselineSeries.setName("Baseline");
        getData().add(baselineSeries);

        // Применяем стиль к серии минимумов
        applyMinimaSeriesStyle();
        applyPeaksSeriesStyle();
        applyBaselineSeriesStyle();
    }

    private void applyMinimaSeriesStyle() {
        // Устанавливаем CSS класс для серии минимумов
        minimaSeries.getNode().setStyle("-fx-background-color: transparent;");

        // Добавляем слушатель для применения стиля к символам
        minimaSeries.nodeProperty().addListener((obs, oldNode, newNode) -> {
            if (newNode != null) {
                Platform.runLater(() -> {
                    // Скрываем линию
                    Node line = newNode.lookup(".chart-series-line");
                    if (line != null) {
                        line.setStyle("-fx-stroke: transparent;");
                    }

                    // Включаем и стилизуем символы
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
    // Обновление данных
    // ────────────────────────────────────────────────────────────────
    public void redraw(SpectrumData data) {
        boolean useAbs = showAbsorbance && data.hasDark && data.hasRef;

        Platform.runLater(() -> {
            // Если график заморожен и есть захваченные данные - не обновляем
            if (frozen && capturedY != null) {
                return;
            }

            // Полностью очищаем и пересоздаём точки — как в C#
            spectrumSeries.getData().clear();

            ObservableList<XYChart.Data<Number, Number>> newPoints = FXCollections.observableArrayList();

            for (int i = 0; i < PIXEL_COUNT; i++) {
                double y = computeYValue(data, i, useAbs);
                newPoints.add(new XYChart.Data<>(i, y));
            }

            spectrumSeries.setData(newPoints);

            // Подгонка Y-оси
            double maxY = newPoints.stream()
                    .mapToDouble(p -> p.getYValue().doubleValue())
                    .max().orElse(4096.0);

            NumberAxis yAxis = (NumberAxis) getYAxis();
            yAxis.setUpperBound(Math.max(4096, maxY * 1.1));
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
        return 4095 - rawValue;
    }

    public void setShowAbsorbance(boolean show) {
        this.showAbsorbance = show;
        // redraw можно вызвать из контроллера после смены чекбокса
    }

    public boolean isFrozen() {
        return frozen;
    }

    public void capture(SpectrumData data) {
        frozen = true;
        capturedY = new double[PIXEL_COUNT];

        // Очищаем минимумы при захвате нового графика
        clearMinima();

        for (int i = 0; i < PIXEL_COUNT; i++) {
            capturedY[i] = computeYValue(data, i, false);
        }
        redrawFromArray(capturedY);
    }

    public void release() {
        frozen = false;
        hasMinima = false; // Сбрасываем флаг минимумов при выходе из режима захвата
    }

    public void redrawFromArray(double[] y) {
        Platform.runLater(() -> {
            spectrumSeries.getData().clear();
            ObservableList<XYChart.Data<Number, Number>> pts = FXCollections.observableArrayList();
            for (int i = 0; i < PIXEL_COUNT; i++) {
                pts.add(new XYChart.Data<>(i, y[i]));
            }
            spectrumSeries.setData(pts);

            // Если были найдены минимумы, перерисовываем их поверх новых данных
            if (hasMinima && !minima.isEmpty()) {
                drawMinimaMarkers();
            }
        });
    }

    // ────────────────────────────────────────────────────────────────
    // Поиск минимумов
    // ────────────────────────────────────────────────────────────────
    public List<Integer> findLocalMinima(int window, double threshold) {
        minima.clear();
        if (!frozen || capturedY == null) {
            System.err.println("The graph is not frozen or there is no captured data!");
            return minima;
        }

        System.out.println("Finding minima in captured data, length capturedY: " + capturedY.length);

        for (int i = window; i < PIXEL_COUNT - window; i++) {
            double v = capturedY[i];
            boolean isMin = true;

            for (int k = 1; k <= window; k++) {
                if (capturedY[i - k] <= v || capturedY[i + k] <= v) {
                    isMin = false;
                    break;
                }
            }

            if (isMin && v < threshold) {
                minima.add(i);
            }
        }

        System.out.println("Minimums found: " + minima.size());
        hasMinima = !minima.isEmpty();
        drawMinimaMarkers();
        return minima;
    }

    private void drawMinimaMarkers() {
        Platform.runLater(() -> {
            minimaSeries.getData().clear();

            System.out.println("Drawing " + minima.size() + " minima markers");

            for (int idx : minima) {
                if (idx >= 0 && idx < capturedY.length) {
                    Data<Number, Number> point = new Data<>(idx, capturedY[idx]);
                    minimaSeries.getData().add(point);

                    // ВАЖНО: Устанавливаем, что для этой точки НУЖЕН символ
                    point.setExtraValue(new Object()); // Любое значение, чтобы триггернуть создание узла

                    // Сразу устанавливаем стиль через CSS класс
                    point.nodeProperty().addListener((obs, oldNode, newNode) -> {
                        if (newNode != null) {
                            // Применяем стиль
                            newNode.setStyle("""
                                -fx-background-color: red;
                                -fx-background-radius: 5px;
                                -fx-padding: 5px;
                                -fx-border-color: white;
                                -fx-border-width: 2px;
                                -fx-border-radius: 5px;
                                -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.8), 5, 0, 0, 0);
                            """);

                            // Добавляем tooltip
                            Tooltip tooltip = new Tooltip(String.format("Pixel: %d\nValue: %.2f\nRaw: %.2f",
                                    idx, capturedY[idx], 4095 - capturedY[idx]));
                            Tooltip.install(newNode, tooltip);

                            System.out.println("Created marker at pixel " + idx);
                        }
                    });
                }
            }

            System.out.println("Added " + minimaSeries.getData().size() + " points to minimaSeries");

            // Принудительно обновляем отображение
            if (minimaSeries.getNode() != null) {
                minimaSeries.getNode().requestFocus();
            }
        });
    }

    public void clearMinima() {
        Platform.runLater(() -> {
            minima.clear();
            minimaSeries.getData().clear();
            hasMinima = false;
        });
    }

    // ────────────────────────────────────────────────────────────────
    // Сглаживание
    // ────────────────────────────────────────────────────────────────
    public void smooth(int window) {
        if (!frozen || capturedY == null) return;

        double[] out = new double[PIXEL_COUNT];

        for (int i = 0; i < PIXEL_COUNT; i++) {
            int a = Math.max(0, i - window);
            int b = Math.min(PIXEL_COUNT - 1, i + window);

            double sum = 0;
            for (int j = a; j <= b; j++) sum += capturedY[j];

            out[i] = sum / (b - a + 1);
        }

        capturedY = out;
        redrawFromArray(capturedY);
        // После сглаживания перерисовываем минимумы если они были
        if (hasMinima) {
            drawMinimaMarkers();
        }
    }

    // ────────────────────────────────────────────────────────────────
    // Масштаб
    // ────────────────────────────────────────────────────────────────
    public void setZoomMode(boolean enabled) {
        zoomMode = enabled;
        setCursor(enabled ? Cursor.CROSSHAIR : Cursor.DEFAULT);
    }

    public boolean canZoomBack() {
        return !backHistory.isEmpty();
    }

    public void zoomBack() {
        if (backHistory.isEmpty()) return;

        forwardHistory.push(currentScale());
        applyScale(backHistory.pop());
    }

    public boolean canZoomForward() {
        return !forwardHistory.isEmpty();
    }

    public void zoomForward() {
        if (forwardHistory.isEmpty()) return;

        backHistory.push(currentScale());
        applyScale(forwardHistory.pop());
    }

    // Tooltip для отображения координат при наведении
    private final Tooltip coordinateTooltip = new Tooltip();

    private void initializeZoom() {
        zoomRect = new Rectangle();
        zoomRect.setManaged(false);
        zoomRect.setVisible(false);
        zoomRect.setStroke(Color.DODGERBLUE);
        zoomRect.setFill(Color.web("#1e90ff33"));
        zoomRect.setStrokeWidth(1.5);

        getPlotChildren().add(zoomRect);

        Platform.runLater(() -> {
            plotArea = lookup(".chart-plot-background");
            if (plotArea != null) {
                // Set all mouse event handlers only on the plot area
                plotArea.setOnMousePressed(this::onZoomStart);
                plotArea.setOnMouseDragged(this::onZoomDrag);
                plotArea.setOnMouseReleased(this::onZoomEnd);
                plotArea.setOnMouseMoved(this::onMouseMove);
                plotArea.setOnMouseEntered(this::onMouseEnter);
                plotArea.setOnMouseExited(this::onMouseExit);
            }
        });
    }

    private void onMouseEnter(MouseEvent e) {
        // Изменяем курсор на крестик при наведении на график
        if (!zoomMode) {
            plotArea.setCursor(Cursor.CROSSHAIR);
        }
    }

    private void onMouseExit(MouseEvent e) {
        // Возвращаем обычный курсор при выходе из графика
        if (!zoomMode) {
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

        // Форматируем координаты для отображения
        String tooltipText = String.format("X: %.2f\nY: %.2f", xValue, yValue);
        coordinateTooltip.setText(tooltipText);

        // Показываем tooltip рядом с курсором
        coordinateTooltip.show(this, e.getScreenX() + 10, e.getScreenY() + 10);
    }

    private void onZoomStart(MouseEvent e) {
        if (!zoomMode || e.getButton() != MouseButton.PRIMARY) return;

        Point2D p = toPlotArea(e);

        dragStartX = p.getX();
        dragStartY = p.getY();

        zoomRect.setX(dragStartX);
        zoomRect.setY(dragStartY);
        zoomRect.setWidth(0);
        zoomRect.setHeight(0);
        zoomRect.setVisible(true);
    }

    private void onZoomDrag(MouseEvent e) {
        if (!zoomMode || !zoomRect.isVisible()) return;

        Point2D p = toPlotArea(e);

        double x = Math.min(p.getX(), dragStartX);
        double y = Math.min(p.getY(), dragStartY);

        zoomRect.setX(x);
        zoomRect.setY(y);
        zoomRect.setWidth(Math.abs(p.getX() - dragStartX));
        zoomRect.setHeight(Math.abs(p.getY() - dragStartY));
    }

    private void onZoomEnd(MouseEvent e) {
        if (!zoomMode || !zoomRect.isVisible()) return;

        zoomRect.setVisible(false);

        if (zoomRect.getWidth() < 10 || zoomRect.getHeight() < 10) return;

        saveBeforeScaleChange();
        applyZoomFromRect();
    }

    private void applyZoomFromRect() {
        NumberAxis xAxis = (NumberAxis) getXAxis();
        NumberAxis yAxis = (NumberAxis) getYAxis();

        double xMin = xAxis.getValueForDisplay(zoomRect.getX()).doubleValue();
        double xMax = xAxis.getValueForDisplay(
                zoomRect.getX() + zoomRect.getWidth()
        ).doubleValue();

        double yMax = yAxis.getValueForDisplay(zoomRect.getY()).doubleValue();
        double yMin = yAxis.getValueForDisplay(
                zoomRect.getY() + zoomRect.getHeight()
        ).doubleValue();

        xAxis.setAutoRanging(false);
        yAxis.setAutoRanging(false);

        xAxis.setLowerBound(xMin);
        xAxis.setUpperBound(xMax);
        yAxis.setLowerBound(yMin);
        yAxis.setUpperBound(yMax);
    }

    private ChartScale currentScale() {
        NumberAxis x = (NumberAxis) getXAxis();
        NumberAxis y = (NumberAxis) getYAxis();

        return new ChartScale(
                x.getLowerBound(), x.getUpperBound(),
                y.getLowerBound(), y.getUpperBound()
        );
    }

    private void applyScale(ChartScale scale) {
        NumberAxis x = (NumberAxis) getXAxis();
        NumberAxis y = (NumberAxis) getYAxis();

        x.setAutoRanging(false);
        y.setAutoRanging(false);

        x.setLowerBound(scale.xMin());
        x.setUpperBound(scale.xMax());
        y.setLowerBound(scale.yMin());
        y.setUpperBound(scale.yMax());
    }

    public boolean isZoomMode() {
        return zoomMode;
    }

    private Point2D toPlotArea(MouseEvent e) {
        return plotArea.sceneToLocal(e.getSceneX(), e.getSceneY());
    }

    private void saveBeforeScaleChange() {
        backHistory.push(currentScale());
        forwardHistory.clear(); // 💥 сбрасываем redo
    }

    // ────────────────────────────────────────────────────────────────
    // Детекция пиков
    // ────────────────────────────────────────────────────────────────
    public List<Peak> detectPeaks(double threshold, int window) {
        peaks.clear();
        
        if (!frozen || capturedY == null) {
            System.err.println("The graph is not frozen or there is no captured data!");
            return peaks;
        }
        
        peakDetectionService.setPeakThreshold(threshold);
        peakDetectionService.setPeakWindow(window);
        
        // Calculate baseline
        baseline = peakDetectionService.calculateBaseline(capturedY);
        
        // Detect peaks
        List<Peak> detectedPeaks = peakDetectionService.detectPeaks(capturedY, 0); // Using 0 baseline for now
        peaks.addAll(detectedPeaks);
        
        // Draw baseline and peaks
        drawBaseline();
        drawPeakMarkers();
        
        return peaks;
    }

    private void drawBaseline() {
        Platform.runLater(() -> {
            baselineSeries.getData().clear();
            
            if (baseline == null) {
                return;
            }
            
            for (int i = 0; i < PIXEL_COUNT; i++) {
                baselineSeries.getData().add(new Data<>(i, baseline[i]));
            }
        });
    }

    private void drawPeakMarkers() {
        Platform.runLater(() -> {
            peaksSeries.getData().clear();
            
            for (Peak peak : peaks) {
                Data<Number, Number> point = new Data<>(peak.getPixel(), capturedY[peak.getPixel()]);
                peaksSeries.getData().add(point);
                
                point.setExtraValue(new Object());
                
                point.nodeProperty().addListener((obs, oldNode, newNode) -> {
                    if (newNode != null) {
                        newNode.setStyle("""
                            -fx-background-color: green;
                            -fx-background-radius: 5px;
                            -fx-padding: 5px;
                            -fx-border-color: white;
                            -fx-border-width: 2px;
                            -fx-border-radius: 5px;
                            -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.8), 5, 0, 0, 0);
                        """);
                        
                        Tooltip tooltip = new Tooltip(String.format("Pixel: %d\nHeight: %.2f\nWidth: %.2f\nArea: %.2f",
                                peak.getPixel(), peak.getHeight(), peak.getWidth(), peak.getArea()));
                        Tooltip.install(newNode, tooltip);
                    }
                });
            }
        });
    }

    public void clearPeaks() {
        Platform.runLater(() -> {
            peaks.clear();
            peaksSeries.getData().clear();
            baseline = null;
            baselineSeries.getData().clear();
        });
    }

    public List<Peak> getPeaks() {
        return peaks;
    }

    public double[] getBaseline() {
        return baseline;
    }

    // Геттер для отладки
    public double[] getCapturedY() {
        return capturedY;
    }

    // Для отладки
    public int getMinimaCount() {
        return minima.size();
    }
}