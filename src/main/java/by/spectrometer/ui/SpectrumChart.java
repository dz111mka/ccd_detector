package by.spectrometer.ui;

import by.spectrometer.model.SpectrumData;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.chart.*;

public class SpectrumChart extends LineChart<Number, Number> {

    // ────────────────────────────────────────────────────────────────
    // Константы и поля
    // ────────────────────────────────────────────────────────────────
    private static final int PIXEL_COUNT = 3648;

    private final ObservableList<Data<Number, Number>> spectrumPoints =
            FXCollections.observableArrayList();

    private final Series<Number, Number> spectrumSeries = new Series<>();

    private boolean showAbsorbance = false;

    // ────────────────────────────────────────────────────────────────
    // Конструктор
    // ────────────────────────────────────────────────────────────────
    public SpectrumChart(SpectrumData initialData) {
        super(new NumberAxis(), new NumberAxis());

        initializeAxes();
        initializeSeries();
        redraw(initialData);  // начальное заполнение
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

        setCreateSymbols(false);
        setAnimated(false);
    }

    // ────────────────────────────────────────────────────────────────
    // Обновление данных
    // ────────────────────────────────────────────────────────────────
    public void redraw(SpectrumData data) {
        boolean useAbs = showAbsorbance && data.hasDark && data.hasRef;

        ObservableList<XYChart.Data<Number, Number>> newData = FXCollections.observableArrayList();

        for (int i = 0; i < PIXEL_COUNT; i++) {
            double y = computeYValue(data, i, useAbs);
            newData.add(new XYChart.Data<>(i, y));
        }

        Platform.runLater(() -> {
            // Важно: очищаем старую серию и ставим новую
            spectrumSeries.getData().clear();           // или можно не очищать, а заменить целиком
            spectrumSeries.setData(newData);            // ← это ключевое
        });

        // Y-ось подгоняем
        double maxY = newData.stream()
                .mapToDouble(d -> d.getYValue().doubleValue())
                .filter(v -> !Double.isNaN(v))
                .max().orElse(4096.0);

        NumberAxis yAxis = (NumberAxis) getYAxis();
        yAxis.setUpperBound(Math.max(4096, maxY * 1.1));
        yAxis.setLowerBound(0);
    }

    private double computeYValue(SpectrumData data, int idx, boolean useAbsorbance) {
        if (useAbsorbance) {
            double denom = data.reference[idx] - data.dark[idx];
            return denom > 50
                    ? -Math.log10((data.raw[idx] - data.dark[idx]) / denom)
                    : 0.0;
        } else {
            return data.raw[idx];
        }
    }

    // ────────────────────────────────────────────────────────────────
    // Absorbance режим
    // ────────────────────────────────────────────────────────────────
    public boolean isShowAbsorbance() {
        return showAbsorbance;
    }

    public void setShowAbsorbance(boolean show) {
        this.showAbsorbance = show;
        // redraw можно вызвать из контроллера после смены чекбокса
    }
}