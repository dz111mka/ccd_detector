package by.spectrometer.model;

public enum ConnectionType {
    WEBSOCKET("WebSocket"),
    SERIAL("Serial"),
    SIMULATOR("Simulator");

    private final String displayName;

    ConnectionType(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
