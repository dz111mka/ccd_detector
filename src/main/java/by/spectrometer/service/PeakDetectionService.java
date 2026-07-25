package by.spectrometer.service;

import by.spectrometer.model.Peak;

import java.util.ArrayList;
import java.util.List;

public class PeakDetectionService {

    private double peakThreshold = 1000;
    private int peakWindow = 50;
    private double baselineSmoothing = 50;

    public List<Peak> detectPeaks(double[] data, double baseline) {
        List<Peak> peaks = new ArrayList<>();

        for (int i = peakWindow; i < data.length - peakWindow; i++) {
            boolean isPeak = true;
            double peakIntensity = data[i] - baseline;

            if (peakIntensity < peakThreshold) {
                continue;
            }

            for (int j = 1; j <= peakWindow; j++) {
                double leftIntensity = data[i - j] - baseline;
                double rightIntensity = data[i + j] - baseline;

                if (leftIntensity > peakIntensity || rightIntensity > peakIntensity) {
                    isPeak = false;
                    break;
                }
            }

            if (isPeak) {
                Peak peak = buildPeak(i, data[i], data, baseline);
                peaks.add(peak);
            }
        }

        return peaks;
    }

    private Peak buildPeak(int pixel, double intensity, double[] data, double baseline) {
        double height = data[pixel] - baseline;

        int leftBoundary = findPeakBoundary(pixel, -1, data, baseline, height);
        int rightBoundary = findPeakBoundary(pixel, 1, data, baseline, height);

        double halfHeight = baseline + height / 2;
        int leftHalf = findHalfHeightBoundary(pixel, -1, data, halfHeight);
        int rightHalf = findHalfHeightBoundary(pixel, 1, data, halfHeight);
        double width = rightHalf - leftHalf;

        double area = calculatePeakArea(pixel, leftBoundary, rightBoundary, data, baseline);

        return new Peak(pixel, intensity, height, width, area, leftBoundary, rightBoundary, baseline, baseline);
    }

    private int findPeakBoundary(int startPixel, int direction, double[] data, double baseline, double peakHeight) {
        int pixel = startPixel + direction;
        double threshold = baseline + peakHeight * 0.1;

        while (pixel > 0 && pixel < data.length - 1) {
            if (data[pixel] <= threshold) {
                break;
            }
            pixel += direction;
        }

        return pixel;
    }

    private int findHalfHeightBoundary(int startPixel, int direction, double[] data, double halfHeight) {
        int pixel = startPixel + direction;

        while (pixel > 0 && pixel < data.length - 1) {
            if (data[pixel] <= halfHeight) {
                return pixel;
            }
            pixel += direction;
        }

        return pixel;
    }

    private double calculatePeakArea(int centerPixel, int leftBoundary, int rightBoundary, double[] data, double baseline) {
        double area = 0;

        for (int i = leftBoundary; i <= rightBoundary; i++) {
            if (i >= 0 && i < data.length) {
                double intensity = data[i] - baseline;
                if (intensity > 0) {
                    area += intensity;
                }
            }
        }

        return area;
    }

    public double[] calculateBaseline(double[] data) {
        double[] baseline = new double[data.length];
        int window = (int) baselineSmoothing;

        for (int i = 0; i < data.length; i++) {
            int start = Math.max(0, i - window);
            int end = Math.min(data.length - 1, i + window);

            double min = Double.MAX_VALUE;
            for (int j = start; j <= end; j++) {
                if (data[j] < min) {
                    min = data[j];
                }
            }

            baseline[i] = min;
        }

        return baseline;
    }

    public double[] applyBaselineCorrection(double[] data, double[] baseline) {
        double[] corrected = new double[data.length];

        for (int i = 0; i < data.length; i++) {
            corrected[i] = data[i] - baseline[i];
        }

        return corrected;
    }

    public double getPeakThreshold() { return peakThreshold; }
    public void setPeakThreshold(double peakThreshold) { this.peakThreshold = peakThreshold; }

    public int getPeakWindow() { return peakWindow; }
    public void setPeakWindow(int peakWindow) { this.peakWindow = peakWindow; }

    public double getBaselineSmoothing() { return baselineSmoothing; }
    public void setBaselineSmoothing(double baselineSmoothing) { this.baselineSmoothing = baselineSmoothing; }
}