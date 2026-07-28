package by.spectrometer.ui.manager;

import by.spectrometer.service.LogService;
import by.spectrometer.ui.SpectrumChart;
import by.spectrometer.util.Constants;
import javafx.scene.chart.XYChart;
import javafx.application.Platform;
import javafx.scene.control.Tooltip;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

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

        double minProminence = calculateMinProminence();
        for (int i = window; i < PIXEL_COUNT - window; i++) {
            double v = capturedY[i];
            boolean isMin = true;

            for (int k = 1; k <= window; k++) {
                if (capturedY[i - k] <= v || capturedY[i + k] <= v) {
                    isMin = false;
                    break;
                }
            }

            if (isMin && v < threshold && hasEnoughProminence(i, window, minProminence)) {
                minima.add(i);
            }
        }

        LogService.log("Minimums found: " + minima.size());
        hasMinima = !minima.isEmpty();
        drawMinimaMarkers();
        return minima;
    }

    private double calculateMinProminence() {
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;

        for (double value : capturedY) {
            min = Math.min(min, value);
            max = Math.max(max, value);
        }

        return Math.max(Constants.MINIMA_MIN_PROMINENCE, (max - min) * 0.05);
    }

    private boolean hasEnoughProminence(int index, int window, double minProminence) {
        double value = capturedY[index];
        double leftMax = maxInRange(index - window, index - 1);
        double rightMax = maxInRange(index + 1, index + window);
        double localProminence = Math.min(leftMax, rightMax) - value;

        return localProminence >= minProminence;
    }

    private double maxInRange(int start, int end) {
        double max = -Double.MAX_VALUE;
        int safeStart = Math.max(0, start);
        int safeEnd = Math.min(capturedY.length - 1, end);

        for (int i = safeStart; i <= safeEnd; i++) {
            max = Math.max(max, capturedY[i]);
        }

        return max;
    }

    public void drawMinimaMarkers() {
        Platform.runLater(() -> {
            chart.getMinimaSeries().getData().clear();
            chart.clearOverlayMarkers(SpectrumChart.MarkerType.MINIMUM);
            chart.clearIndicatorLines(SpectrumChart.MarkerType.MINIMUM);

            for (int idx : minima) {
                if (idx >= 0 && idx < capturedY.length) {
                    chart.addIndicatorLine(SpectrumChart.MarkerType.MINIMUM, idx, capturedY[idx], "#ff3b30");
                    chart.addOverlayMarker(
                            SpectrumChart.MarkerType.MINIMUM,
                            idx,
                            capturedY[idx],
                            createMinimaMarker(idx, capturedY[idx])
                    );
                }
            }

            if (chart.getMinimaSeries().getNode() != null) {
                chart.getMinimaSeries().getNode().requestFocus();
            }
        });
    }

    private Circle createMinimaMarker(int pixel, double value) {
        Circle marker = new Circle(6);
        marker.setManaged(false);
        marker.setMouseTransparent(false);
        marker.setFill(Color.web("#ff3b30"));
        marker.setStroke(Color.WHITE);
        marker.setStrokeWidth(2);
        marker.setStyle("-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.85), 6, 0, 0, 1);");

        Tooltip.install(marker, new Tooltip(String.format(
                "Minimum\n%s\nValue: %.2f\nRaw: %.2f",
                chart.formatXValueForPixel(pixel), value, 4095 - value
        )));
        marker.setOnMouseClicked(event -> {
            chart.selectCalibrationPixel(pixel);
            event.consume();
        });
        return marker;
    }

    public void clearMinima() {
        Platform.runLater(() -> {
            minima.clear();
            chart.getMinimaSeries().getData().clear();
            chart.clearOverlayMarkers(SpectrumChart.MarkerType.MINIMUM);
            chart.clearIndicatorLines(SpectrumChart.MarkerType.MINIMUM);
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
                pts.add(new XYChart.Data<>(chart.getXValueForPixel(i), y[i]));
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
