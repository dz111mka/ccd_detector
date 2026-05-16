package by.spectrometer.ui.manager;

import by.spectrometer.model.Peak;
import by.spectrometer.service.PeakDetectionService;
import by.spectrometer.ui.SpectrumChart;
import javafx.application.Platform;
import javafx.scene.chart.XYChart;

import java.util.ArrayList;
import java.util.List;

public class ChartPeakController {

    private static final int PIXEL_COUNT = 3648;

    private final SpectrumChart chart;
    private final PeakDetectionService peakDetectionService;

    private final List<Peak> peaks = new ArrayList<>();
    private double[] baseline = null;

    public ChartPeakController(SpectrumChart chart) {
        this.chart = chart;
        this.peakDetectionService = new PeakDetectionService();
    }

    public List<Peak> detectPeaks(double threshold, int window) {
        peaks.clear();

        if (!chart.isFrozen() || chart.getCapturedY() == null) {
            System.err.println("The graph is not frozen or there is no captured data!");
            return peaks;
        }

        peakDetectionService.setPeakThreshold(threshold);
        peakDetectionService.setPeakWindow(window);

        baseline = peakDetectionService.calculateBaseline(chart.getCapturedY());

        List<Peak> detectedPeaks = peakDetectionService.detectPeaks(chart.getCapturedY(), 0);
        peaks.addAll(detectedPeaks);

        drawBaseline();
        drawPeakMarkers();

        return peaks;
    }

    private void drawBaseline() {
        Platform.runLater(() -> {
            chart.getBaselineSeries().getData().clear();

            if (baseline == null) {
                return;
            }

            for (int i = 0; i < PIXEL_COUNT; i++) {
                chart.getBaselineSeries().getData().add(new XYChart.Data<>(i, baseline[i]));
            }
        });
    }

    private void drawPeakMarkers() {
        Platform.runLater(() -> {
            chart.getPeaksSeries().getData().clear();

            for (Peak peak : peaks) {
                XYChart.Data<Number, Number> point = new XYChart.Data<>(peak.pixel(), chart.getCapturedY()[peak.pixel()]);
                chart.getPeaksSeries().getData().add(point);

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

                        javafx.scene.control.Tooltip tooltip = new javafx.scene.control.Tooltip(String.format("Pixel: %d\nHeight: %.2f\nWidth: %.2f\nArea: %.2f",
                                peak.pixel(), peak.height(), peak.width(), peak.area()));
                        javafx.scene.control.Tooltip.install(newNode, tooltip);
                    }
                });
            }
        });
    }

    public void clearPeaks() {
        Platform.runLater(() -> {
            peaks.clear();
            chart.getPeaksSeries().getData().clear();
            baseline = null;
            chart.getBaselineSeries().getData().clear();
        });
    }

    public List<Peak> getPeaks() {
        return peaks;
    }

    public double[] getBaseline() {
        return baseline;
    }
}