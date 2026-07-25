package by.spectrometer.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpectrumDataTest {

    @Test
    void averagesDarkAndReferenceBuffers() {
        SpectrumData data = new SpectrumData();

        double[] first = filledFrame(10);
        double[] second = filledFrame(14);

        data.accumulateFrame(first, SpectrumData.BufferType.DARK);
        data.accumulateFrame(second, SpectrumData.BufferType.DARK);
        data.finalizeDarkBuffer(2);

        data.accumulateFrame(filledFrame(100), SpectrumData.BufferType.REFERENCE);
        data.accumulateFrame(filledFrame(120), SpectrumData.BufferType.REFERENCE);
        data.finalizeReferenceBuffer(2);

        assertEquals(12.0, data.dark[0]);
        assertEquals(110.0, data.reference[0]);
        assertTrue(data.hasDark);
        assertTrue(data.hasRef);
        assertTrue(data.isTransmissionReady());
    }

    private static double[] filledFrame(double value) {
        double[] frame = new double[3648];
        for (int i = 0; i < frame.length; i++) {
            frame[i] = value;
        }
        return frame;
    }
}
