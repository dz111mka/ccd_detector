package by.spectrometer.model;

public enum SimulationTemplate {
    DEUTERIUM_UV_CLEAR_QUARTZ("UV deuterium + clear quartz", 260.0, 365.0, 486.0),
    MERCURY_ARGON_CALIBRATION("Hg-Ar calibration lamp", 253.65, 365.02, 404.66, 435.83, 546.07, 576.96, 696.54, 763.51, 811.53),
    TUNGSTEN_VISIBLE_NEUTRAL("Tungsten visible + neutral filter"),
    WHITE_LED_BLUE_BLOCKER("White LED + blue blocker coating", 440.0),
    HALOGEN_IR_HEAT_MIRROR("Halogen/IR + heat mirror coating", 780.0),
    NIR_LED_BANDPASS_940("NIR LED + 940 nm bandpass", 940.0),
    CHLOROPHYLL_VISIBLE("Visible source + chlorophyll-like sample", 430.0, 662.0),
    PHASE_PLATE_BROADBAND("Broadband source + phase plate ripple");

    private final String displayName;
    private final double[] referenceLinesNm;

    SimulationTemplate(String displayName, double... referenceLinesNm) {
        this.displayName = displayName;
        this.referenceLinesNm = referenceLinesNm;
    }

    public double[] getReferenceLinesNm() {
        return referenceLinesNm.clone();
    }

    @Override
    public String toString() {
        return displayName;
    }
}
