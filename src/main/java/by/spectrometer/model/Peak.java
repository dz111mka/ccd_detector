package by.spectrometer.model;

public record Peak(
        int pixel,
        double intensity,
        double height,
        double width,
        double area,
        double leftPixel,
        double rightPixel,
        double leftBase,
        double rightBase,
        double fwhm,
        double gaussianR2,
        double lorentzianR2,
        String bestFit) {

    public Peak(int pixel, double intensity) {
        this(pixel, intensity, 0, 0, 0, 0, 0, 0, 0);
    }

    public Peak(int pixel, double intensity, double height, double width, double area,
                double leftPixel, double rightPixel, double leftBase, double rightBase) {
        this(pixel, intensity, height, width, area, leftPixel, rightPixel, leftBase, rightBase,
                width, 0, 0, "n/a");
    }
}
