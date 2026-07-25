package by.spectrometer.service;

import by.spectrometer.model.CalibrationPoint;
import by.spectrometer.model.SpectrumData;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WavelengthCalibrationServiceTest {

    @Test
    void appliesPiecewiseLinearCalibrationFromKnownPoints() {
        SpectrumData data = new SpectrumData();
        WavelengthCalibrationService service = new WavelengthCalibrationService();

        service.applyPiecewiseLinearCalibration(data, List.of(
                new CalibrationPoint(100, 400),
                new CalibrationPoint(300, 500),
                new CalibrationPoint(700, 900)
        ));

        assertTrue(data.wavelengthCalibrated);
        assertEquals(450.0, data.wavelength[200]);
        assertEquals(600.0, data.wavelength[400]);
    }
}
