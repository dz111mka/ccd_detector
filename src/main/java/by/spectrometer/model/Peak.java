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
        double rightBase) {

    public Peak(int pixel, double intensity) {
        this(pixel, intensity, 0, 0, 0, 0, 0, 0, 0);
    }
}
