package by.spectrometer.manager;

import by.spectrometer.controller.SpectrometerController;
import by.spectrometer.model.Peak;
import by.spectrometer.model.SpectrumData;
import by.spectrometer.service.LogService;
import by.spectrometer.ui.SpectrumChart;
import by.spectrometer.util.Constants;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;

import java.util.Arrays;
import java.util.DoubleSummaryStatistics;
import java.util.List;

public class MeasurementManager {

    private final SpectrometerController controller;
    private final SpectrumChart chart;

    public MeasurementManager(SpectrometerController controller, SpectrumChart chart) {
        this.controller = controller;
        this.chart = chart;
    }

    public void toggleCaptureMode(javafx.scene.control.Button btnCapture) {
        if (!chart.isFrozen()) {
            chart.capture(controller.getData());
            btnCapture.setText("Live ON");
        } else {
            chart.release();
            btnCapture.setText("Захватить");
        }
    }

    public void applySmoothing() {
        if (!chart.isFrozen()) {
            LogService.log("Smooth is only available in Capture mode.");
            return;
        }
        chart.smooth(Constants.SMOOTHING_WINDOW_SIZE);
    }

    public void detectPeaks(TextField tfPeakThreshold, TextField tfPeakWindow) {
        if (!validatePeakDetection()) return;

        try {
            double threshold = Double.parseDouble(tfPeakThreshold.getText());
            int window = Integer.parseInt(tfPeakWindow.getText());

            List<Peak> peaks = chart.detectPeaks(threshold, window);
            LogService.log("Найдено пиков: " + peaks.size());

            for (Peak peak : peaks) {
                LogService.log(String.format("Пик в пикселе %d: высота=%.2f, ширина=%.2f, площадь=%.2f",
                        peak.pixel(), peak.height(), peak.width(), peak.area()));
            }
        } catch (NumberFormatException e) {
            LogService.log("Ошибка: порог и окно должны быть числами");
        }
    }

    private boolean validatePeakDetection() {
        if (!chart.isFrozen()) {
            LogService.log("Детекция пиков доступна только в режиме Capture.");
            return false;
        }

        if (chart.getCapturedY() == null) {
            LogService.log("Детекция пиков не возможна: нет захваченных данных.");
            return false;
        }

        return true;
    }

    public void findMinima() {
        if (!validateMinimaSearch()) return;

        LogService.log("Finding minimums on a captured chart...");
        logCapturedDataRange();

        chart.clearMinima();
        List<Integer> minima = chart.findLocalMinima(50, 3000);

        LogService.log("Minimums found: " + minima.size());
        if (minima.isEmpty()) {
            searchWithDifferentThresholds();
        } else {
            logMinimaDetails(minima);
        }
    }

    private boolean validateMinimaSearch() {
        if (!chart.isFrozen()) {
            LogService.log("Minimum search is only available in Capture mode.");
            LogService.log("The chart is frozen: " + chart.isFrozen());
            LogService.log("capturedY: " + (chart.getCapturedY() != null ? "не null" : "null"));
            return false;
        }
        return true;
    }

    private void logCapturedDataRange() {
        if (chart.getCapturedY() != null) {
            DoubleSummaryStatistics stats = Arrays.stream(chart.getCapturedY())
                    .summaryStatistics();
            LogService.log(String.format("capturedY range: min=%.2f, max=%.2f",
                    stats.getMin(), stats.getMax()));
        }
    }

    private void searchWithDifferentThresholds() {
        double[] thresholds = {1000, 1500, 2000, 2500, 3000, 3500};
        for (double threshold : thresholds) {
            List<Integer> minima = chart.findLocalMinima(50, threshold);
            LogService.log(String.format("  Threshold %.0f: found %d minima", threshold, minima.size()));
            if (!minima.isEmpty()) {
                chart.clearMinima();
                chart.findLocalMinima(50, threshold);
                break;
            }
        }
    }

    private void logMinimaDetails(List<Integer> minima) {
        for (int idx : minima) {
            double yValue = chart.getCapturedY() != null ? chart.getCapturedY()[idx] : 0;
            LogService.log(String.format("  Pixel %d: Y=%.2f, RawValue=%.2f",
                    idx, yValue, 4095 - yValue));
        }
    }

    public void recordDarkSignal() {
        SpectrumData data = controller.getData();
        data.recordingMode = SpectrumData.BufferType.DARK;
        data.bufferAccumulatorCount = 0;
        data.darkBufferReady = false;
        Arrays.fill(data.darkBuffer, 0.0);
        LogService.log("Recording dark signal... (" + Constants.TRANSMISSION_BUFFER_FRAMES + " frames)");
    }

    public void recordReferenceSignal() {
        SpectrumData data = controller.getData();
        if (!data.darkBufferReady) {
            LogService.log("Dark signal not ready. Record dark signal first.");
            return;
        }
        data.recordingMode = SpectrumData.BufferType.REFERENCE;
        data.bufferAccumulatorCount = 0;
        data.referenceBufferReady = false;
        Arrays.fill(data.referenceBuffer, 0.0);
        LogService.log("Recording reference signal... (" + Constants.TRANSMISSION_BUFFER_FRAMES + " frames)");
    }

    public void toggleTransmissionMode(Button btnTransmissionMode) {
        SpectrumData data = controller.getData();

        if (data.displayMode == SpectrumData.DisplayMode.INTENSITY) {
            if (!data.darkBufferReady) {
                LogService.log("❌ Dark сигнал не записан. Нажмите 'Тёмный ток'");
                return;
            }
            if (!data.referenceBufferReady) {
                LogService.log("❌ Reference сигнал не записан. Нажмите 'Белая опора'");
                return;
            }

            data.displayMode = SpectrumData.DisplayMode.TRANSMISSION;
            btnTransmissionMode.setText("Intensity Mode");
            chart.setTransmissionMode(true);
            LogService.log("✅ Переключено в режим TRANSMISSION (0-100%)");

            // ВАЖНО: Принудительно обновляем график с новым режимом
            chart.forceRedraw(data);
        } else {
            data.displayMode = SpectrumData.DisplayMode.INTENSITY;
            btnTransmissionMode.setText("Transmission Mode");
            chart.setTransmissionMode(false);
            LogService.log("✅ Переключено в режим INTENSITY");

            // ВАЖНО: Принудительно обновляем график
            chart.forceRedraw(data);
        }
    }

    public void clearBuffers() {
        SpectrumData data = controller.getData();
        data.hasDark = false;
        data.hasRef = false;
        data.darkBufferReady = false;
        data.referenceBufferReady = false;
        data.bufferAccumulatorCount = 0;
        data.recordingMode = SpectrumData.BufferType.NONE;
        Arrays.fill(data.dark, 0.0);
        Arrays.fill(data.reference, 0.0);
        Arrays.fill(data.darkBuffer, 0.0);
        Arrays.fill(data.referenceBuffer, 0.0);
        LogService.log("Buffers cleared.");
    }
}