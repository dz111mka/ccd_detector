package by.spectrometer.ui;

import by.spectrometer.model.SpectrumData;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.chart.*;

public class SpectrumChart extends LineChart<Number, Number> {

    private final Series<Number, Number> spectrum = new Series<>();
    private final Series<Number, Number> dark = new Series<>();
    private final Series<Number, Number> reference = new Series<>();
    private final ObservableList<Data<Number, Number>> spectrumPoints =
            FXCollections.observableArrayList();

    private boolean showAbsorbance = false;

    public SpectrumChart(SpectrumData data) {
        super(new NumberAxis(190, 2050, 200), new NumberAxis());
        ((NumberAxis)getXAxis()).setLabel("Длина волны, нм");
        getYAxis().setLabel("Интенсивность / Absorbance");
        setTitle("Спектр TCD1304");

        for (int i = 0; i < 3648; i++) {
            spectrumPoints.add(new XYChart.Data<>(0, 0));
        }
        spectrum.setData(spectrumPoints);

        spectrum.setName("Спектр");
        dark.setName("Тёмный ток");
        reference.setName("Опорный");
        setCreateSymbols(false);   // уже есть — хорошо
        setAlternativeRowFillVisible(false);
        setAlternativeColumnFillVisible(false);

        getData().addAll(spectrum, dark, reference);

        redraw(data);
    }

    public void redraw(SpectrumData data) {
        boolean showAbs = showAbsorbance && data.hasDark && data.hasRef;

        int idx = 0;
        for (XYChart.Data<Number, Number> p : spectrumPoints) {
            double x = data.wavelength[idx];
            double y;

            if (showAbs) {
                double denom = data.reference[idx] - data.dark[idx];
                y = denom > 50 ? -Math.log10((data.raw[idx] - data.dark[idx]) / denom) : 0;
            } else {
                y = data.raw[idx];
            }

            p.setXValue(x);
            p.setYValue(y);
            idx++;
        }
    }

    public void setShowAbsorbance(boolean show) {
        this.showAbsorbance = show;
    }
}