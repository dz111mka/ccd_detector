package by.spectrometer.ui.manager;

import by.spectrometer.service.LogService;
import by.spectrometer.ui.SpectrumChart;
import javafx.scene.chart.XYChart;
import javafx.application.Platform;

import java.util.ArrayList;
import java.util.List;

public class ChartDataProcessor {

    private static final int PIXEL_COUNT = 3648;

    private final SpectrumChart chart;

    private final List<Integer> minima = new ArrayList<>();
    private boolean hasMinima = false;

    private double[] capturedY = null;

    public ChartDataProcessor(SpectrumChart chart) {
        this.chart = chart;
    }

    public List<Integer> findLocalMinima(int window, double threshold) {
        minima.clear();
        if (!chart.isFrozen() || capturedY == null) {
            LogService.log("The graph is not frozen or there is no captured data.");
            return minima;
        }

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

        LogService.log("Minimums found: " + minima.size());
        hasMinima = !minima.isEmpty();
        drawMinimaMarkers();
        return minima;
    }

    public void drawMinimaMarkers() {
        Platform.runLater(() -> {
            chart.getMinimaSeries().getData().clear();

            for (int idx : minima) {
                if (idx >= 0 && idx < capturedY.length) {
                    XYChart.Data<Number, Number> point = new XYChart.Data<>(idx, capturedY[idx]);
                    chart.getMinimaSeries().getData().add(point);

                    point.setExtraValue(new Object());

                    point.nodeProperty().addListener((obs, oldNode, newNode) -> {
                        if (newNode != null) {
                            newNode.setStyle("""
                                -fx-background-color: red;
                                -fx-background-radius: 5px;
                                -fx-padding: 5px;
                                -fx-border-color: white;
                                -fx-border-width: 2px;
                                -fx-border-radius: 5px;
                                -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.8), 5, 0, 0, 0);
                            """);

                            javafx.scene.control.Tooltip tooltip = new javafx.scene.control.Tooltip(String.format("Pixel: %d\nValue: %.2f\nRaw: %.2f",
                                    idx, capturedY[idx], 4095 - capturedY[idx]));
                            javafx.scene.control.Tooltip.install(newNode, tooltip);
                        }
                    });
                }
            }

            if (chart.getMinimaSeries().getNode() != null) {
                chart.getMinimaSeries().getNode().requestFocus();
            }
        });
    }

    public void clearMinima() {
        Platform.runLater(() -> {
            minima.clear();
            chart.getMinimaSeries().getData().clear();
            hasMinima = false;
        });
    }

    public void smooth(int window) {
        if (!chart.isFrozen() || capturedY == null) return;

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
        if (hasMinima) {
            drawMinimaMarkers();
        }
    }

    private void redrawFromArray(double[] y) {
        Platform.runLater(() -> {
            chart.getSpectrumSeries().getData().clear();
            javafx.collections.ObservableList<XYChart.Data<Number, Number>> pts = javafx.collections.FXCollections.observableArrayList();
            for (int i = 0; i < PIXEL_COUNT; i++) {
                pts.add(new XYChart.Data<>(i, y[i]));
            }
            chart.getSpectrumSeries().setData(pts);

            if (hasMinima && !minima.isEmpty()) {
                drawMinimaMarkers();
            }
        });
    }

    public double[] getCapturedY() {
        return capturedY;
    }

    public void setCapturedY(double[] capturedY) {
        this.capturedY = capturedY;
    }

    public boolean hasMinima() {
        return hasMinima;
    }

    public List<Integer> getMinima() {
        return minima;
    }
}
