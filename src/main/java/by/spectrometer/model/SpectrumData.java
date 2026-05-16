package by.spectrometer.model;

public class SpectrumData {
    public final double[] wavelength = new double[3648];
    public final double[] raw        = new double[3648];
    public final double[] dark       = new double[3648];
    public final double[] reference  = new double[3648];

    public volatile FrameType currentFrameType = FrameType.SPECTRUM;

    public boolean hasDark = false;
    public boolean hasRef  = false;
}
