package by.spectrometer.model;

public class SpectrumData {
    public final double[] wavelength = new double[3648];
    public final double[] raw = new double[3648];
    public final double[] dark = new double[3648];
    public final double[] reference = new double[3648];
    public boolean wavelengthCalibrated = false;

    public boolean hasDark = false;
    public boolean hasRef = false;

    public enum DisplayMode {
        INTENSITY, TRANSMISSION
    }

    public DisplayMode displayMode = DisplayMode.INTENSITY;

    public double[] darkBuffer = new double[3648];
    public double[] referenceBuffer = new double[3648];
    public boolean darkBufferReady = false;
    public boolean referenceBufferReady = false;
    public int bufferAccumulatorCount = 0;

    public enum BufferType {
        DARK, REFERENCE, NONE
    }

    public BufferType recordingMode = BufferType.NONE;

    public void accumulateFrame(double[] raw, BufferType type) {
        if (type == BufferType.DARK) {
            for (int i = 0; i < raw.length; i++) {
                darkBuffer[i] += raw[i];
            }
        } else {
            for (int i = 0; i < raw.length; i++) {
                referenceBuffer[i] += raw[i];
            }
        }
        bufferAccumulatorCount++;
    }

    public void finalizeDarkBuffer(int frameCount) {
        for (int i = 0; i < darkBuffer.length; i++) {
            dark[i] = darkBuffer[i] / frameCount;
        }
        hasDark = true;
        darkBufferReady = true;
    }

    public void finalizeReferenceBuffer(int frameCount) {
        for (int i = 0; i < referenceBuffer.length; i++) {
            reference[i] = referenceBuffer[i] / frameCount;
        }
        hasRef = true;
        referenceBufferReady = true;
    }

    public boolean isTransmissionReady() {
        return darkBufferReady && referenceBufferReady;
    }
}
