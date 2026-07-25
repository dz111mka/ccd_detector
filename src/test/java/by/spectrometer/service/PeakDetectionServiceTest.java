package by.spectrometer.service;

import by.spectrometer.model.Peak;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PeakDetectionServiceTest {

    @Test
    void detectsSingleLocalPeakAboveThreshold() {
        PeakDetectionService service = new PeakDetectionService();
        service.setPeakThreshold(50);
        service.setPeakWindow(2);

        double[] data = new double[21];
        data[8] = 20;
        data[9] = 70;
        data[10] = 120;
        data[11] = 70;
        data[12] = 20;

        List<Peak> peaks = service.detectPeaks(data, 0);

        assertEquals(1, peaks.size());
        Peak peak = peaks.getFirst();
        assertEquals(10, peak.pixel());
        assertTrue(peak.height() > 50);
        assertTrue(peak.fwhm() > 0);
        assertTrue(peak.area() > 0);
        assertTrue(peak.gaussianR2() >= 0);
        assertTrue(peak.lorentzianR2() >= 0);
        assertFalse(peak.bestFit().isBlank());
    }
}
