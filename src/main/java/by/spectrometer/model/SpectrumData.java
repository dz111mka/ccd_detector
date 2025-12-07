package by.spectrometer.model;

public class SpectrumData {
    public final double[] wavelength;
    public final double[] raw;
    public final double[] dark;
    public final double[] reference;

    public boolean hasDark = false;
    public boolean hasRef = false;

    public SpectrumData() {
        this.wavelength = new double[256];
        this.raw = new double[256];
        this.dark = new double[256];
        this.reference = new double[256];
    }
}