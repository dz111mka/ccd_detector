package by.spectrometer.model;

public enum SimulationTemplate {
    DEUTERIUM_UV_CLEAR_QUARTZ("UV deuterium + clear quartz"),
    MERCURY_ARGON_CALIBRATION("Hg-Ar calibration lamp"),
    TUNGSTEN_VISIBLE_NEUTRAL("Tungsten visible + neutral filter"),
    WHITE_LED_BLUE_BLOCKER("White LED + blue blocker coating"),
    HALOGEN_IR_HEAT_MIRROR("Halogen/IR + heat mirror coating"),
    NIR_LED_BANDPASS_940("NIR LED + 940 nm bandpass"),
    CHLOROPHYLL_VISIBLE("Visible source + chlorophyll-like sample"),
    PHASE_PLATE_BROADBAND("Broadband source + phase plate ripple");

    private final String displayName;

    SimulationTemplate(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
