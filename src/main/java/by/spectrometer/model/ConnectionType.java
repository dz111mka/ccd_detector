package by.spectrometer.model;

public enum ConnectionType {
    WEBSOCKET("WebSocket (ESP32)"),
    SERIAL("Serial (Arduino)");

    private final String displayName;

    ConnectionType(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}