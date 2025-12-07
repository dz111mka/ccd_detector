package by.spectrometer.ui;

import by.spectrometer.model.SpectrumData;
import javafx.scene.chart.*;

public class SpectrumChart extends LineChart<Number, Number> {

    private final Series<Number, Number> spectrum = new Series<>();
    private final Series<Number, Number> dark = new Series<>();
    private final Series<Number, Number> reference = new Series<>();

    private boolean showAbsorbance = false;

    public SpectrumChart(SpectrumData data) {
        super(new NumberAxis(190, 2050, 200), new NumberAxis());
        ((NumberAxis)getXAxis()).setLabel("Длина волны, нм");
        getYAxis().setLabel("Интенсивность / Absorbance");
        setTitle("Спектр TCD1304");
        setCreateSymbols(false);

        spectrum.setName("Спектр");
        dark.setName("Тёмный ток");
        reference.setName("Опорный");

        getData().addAll(spectrum, dark, reference);

        redraw(data);
    }

    public void redraw(SpectrumData data) {
        spectrum.getData().clear();
        dark.getData().clear();
        reference.getData().clear();

        for (int i = 0; i < 256; i++) {
            double x = data.wavelength[i];

            if (data.hasDark) dark.getData().add(new Data<>(x, data.dark[i]));
            if (data.hasRef)  reference.getData().add(new Data<>(x, data.reference[i]));

            double value;
            if (showAbsorbance && data.hasDark && data.hasRef) {
                double denom = data.reference[i] - data.dark[i];
                value = denom > 50 ? -Math.log10((data.raw[i] - data.dark[i]) / denom) : 0;
            } else {
                value = data.raw[i];
            }
            spectrum.getData().add(new Data<>(x, value));
        }
    }

    public void setShowAbsorbance(boolean show) {
        this.showAbsorbance = show;
    }
}