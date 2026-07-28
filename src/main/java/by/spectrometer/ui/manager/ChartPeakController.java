package by.spectrometer.ui.manager;

import by.spectrometer.model.Peak;
import by.spectrometer.service.LogService;
import by.spectrometer.service.PeakDetectionService;
import by.spectrometer.ui.SpectrumChart;
import javafx.application.Platform;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Tooltip;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

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
            LogService.log("The graph is not frozen or there is no captured data.");
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
                chart.getBaselineSeries().getData().add(new XYChart.Data<>(chart.getXValueForPixel(i), baseline[i]));
            }
        });
    }

    private void drawPeakMarkers() {
        Platform.runLater(() -> {
            chart.getPeaksSeries().getData().clear();
            chart.clearOverlayMarkers(SpectrumChart.MarkerType.PEAK);
            chart.clearIndicatorLines(SpectrumChart.MarkerType.PEAK);

            for (Peak peak : peaks) {
                chart.addIndicatorLine(SpectrumChart.MarkerType.PEAK, peak.pixel(), chart.getCapturedY()[peak.pixel()], "#30d158");
                chart.addOverlayMarker(
                        SpectrumChart.MarkerType.PEAK,
                        peak.pixel(),
                        chart.getCapturedY()[peak.pixel()],
                        createPeakMarker(peak)
                );
            }
        });
    }

    private Circle createPeakMarker(Peak peak) {
        Circle marker = new Circle(6);
        marker.setManaged(false);
        marker.setMouseTransparent(false);
        marker.setFill(Color.web("#30d158"));
        marker.setStroke(Color.WHITE);
        marker.setStrokeWidth(2);
        marker.setStyle("-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.85), 6, 0, 0, 1);");

        Tooltip.install(marker, new Tooltip(String.format(
                "Peak\n%s\nHeight: %.2f\nFWHM: %.2f\nArea: %.2f\nBest fit: %s\nGaussian R²: %.3f\nLorentzian R²: %.3f",
                chart.formatXValueForPixel(peak.pixel()), peak.height(), peak.fwhm(), peak.area(), peak.bestFit(),
                peak.gaussianR2(), peak.lorentzianR2()
        )));
        marker.setOnMouseClicked(event -> {
            chart.selectCalibrationPixel(peak.pixel());
            event.consume();
        });
        return marker;
    }

    public void clearPeaks() {
        Platform.runLater(() -> {
            peaks.clear();
            chart.getPeaksSeries().getData().clear();
            chart.clearOverlayMarkers(SpectrumChart.MarkerType.PEAK);
            chart.clearIndicatorLines(SpectrumChart.MarkerType.PEAK);
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
