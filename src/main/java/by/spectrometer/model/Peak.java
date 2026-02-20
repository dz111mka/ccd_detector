package by.spectrometer.model;

import java.util.ArrayList;
import java.util.List;

public class Peak {
    private int pixel;
    private double intensity;
    private double height;
    private double width;
    private double area;
    private double leftPixel;
    private double rightPixel;
    private double leftBase;
    private double rightBase;

    public Peak(int pixel, double intensity) {
        this.pixel = pixel;
        this.intensity = intensity;
    }

    // Getters and Setters
    public int getPixel() { return pixel; }
    public void setPixel(int pixel) { this.pixel = pixel; }

    public double getIntensity() { return intensity; }
    public void setIntensity(double intensity) { this.intensity = intensity; }

    public double getHeight() { return height; }
    public void setHeight(double height) { this.height = height; }

    public double getWidth() { return width; }
    public void setWidth(double width) { this.width = width; }

    public double getArea() { return area; }
    public void setArea(double area) { this.area = area; }

    public double getLeftPixel() { return leftPixel; }
    public void setLeftPixel(double leftPixel) { this.leftPixel = leftPixel; }

    public double getRightPixel() { return rightPixel; }
    public void setRightPixel(double rightPixel) { this.rightPixel = rightPixel; }

    public double getLeftBase() { return leftBase; }
    public void setLeftBase(double leftBase) { this.leftBase = leftBase; }

    public double getRightBase() { return rightBase; }
    public void setRightBase(double rightBase) { this.rightBase = rightBase; }

    @Override
    public String toString() {
        return String.format("Peak at pixel %d: height=%.2f, width=%.2f, area=%.2f", 
                pixel, height, width, area);
    }
}