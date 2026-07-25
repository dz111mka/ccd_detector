package by.spectrometer.service;

import by.spectrometer.model.CalibrationPoint;
import by.spectrometer.model.SpectrumData;

import java.util.Comparator;
import java.util.List;

public class WavelengthCalibrationService {

    public void applyPiecewiseLinearCalibration(SpectrumData data, List<CalibrationPoint> points) {
        if (points.size() < 2) {
            throw new IllegalArgumentException("At least two calibration points are required");
        }

        List<CalibrationPoint> sorted = points.stream()
                .sorted(Comparator.comparingDouble(CalibrationPoint::pixel))
                .toList();

        validatePoints(sorted);

        for (int pixel = 0; pixel < data.wavelength.length; pixel++) {
            data.wavelength[pixel] = interpolate(pixel, sorted);
        }
        data.wavelengthCalibrated = true;
    }

    public void clearCalibration(SpectrumData data) {
        data.wavelengthCalibrated = false;
    }

    private void validatePoints(List<CalibrationPoint> points) {
        for (int i = 1; i < points.size(); i++) {
            CalibrationPoint previous = points.get(i - 1);
            CalibrationPoint current = points.get(i);

            if (current.pixel() == previous.pixel()) {
                throw new IllegalArgumentException("Calibration points must use unique pixels");
            }
            if (current.wavelengthNm() <= previous.wavelengthNm()) {
                throw new IllegalArgumentException("Wavelengths must increase with pixel positions");
            }
        }
    }

    private double interpolate(int pixel, List<CalibrationPoint> points) {
        if (pixel <= points.getFirst().pixel()) {
            return extrapolate(pixel, points.get(0), points.get(1));
        }

        int last = points.size() - 1;
        if (pixel >= points.get(last).pixel()) {
            return extrapolate(pixel, points.get(last - 1), points.get(last));
        }

        for (int i = 1; i < points.size(); i++) {
            CalibrationPoint left = points.get(i - 1);
            CalibrationPoint right = points.get(i);
            if (pixel <= right.pixel()) {
                return extrapolate(pixel, left, right);
            }
        }

        return points.get(last).wavelengthNm();
    }

    private double extrapolate(double pixel, CalibrationPoint left, CalibrationPoint right) {
        double slope = (right.wavelengthNm() - left.wavelengthNm()) / (right.pixel() - left.pixel());
        return left.wavelengthNm() + (pixel - left.pixel()) * slope;
    }
}
