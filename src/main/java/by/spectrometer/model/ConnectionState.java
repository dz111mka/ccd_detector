package by.spectrometer.model;

import javafx.beans.property.*;

public class ConnectionState {
    private final StringProperty status = new SimpleStringProperty("Не подключено");
    private final StringProperty url = new SimpleStringProperty("");
    private final BooleanProperty connected = new SimpleBooleanProperty(false);

    public StringProperty statusProperty() {
        return status;
    }

    public StringProperty urlProperty() {
        return url;
    }

    public BooleanProperty connectedProperty() {
        return connected;
    }

    public void setConnected(String url) {
        this.url.set(url);
        this.status.set("Подключено: " + url);
        this.connected.set(true);
    }

    public void setDisconnected(String reason) {
        this.status.set("Отключено" + (reason.isEmpty() ? "" : " (" + reason + ")"));
        this.connected.set(false);
    }

    public void setConnecting() {
        this.status.set("Подключение...");
    }
}
