package by.spectrometer.model;

import javafx.beans.property.*;

public class ConnectionState {
    private final StringProperty status = new SimpleStringProperty("Не подключено");
    private final StringProperty connectionType = new SimpleStringProperty("");
    private final StringProperty address = new SimpleStringProperty("");
    private final BooleanProperty connected = new SimpleBooleanProperty(false);

    public StringProperty statusProperty() {
        return status;
    }

    public StringProperty connectionTypeProperty() {
        return connectionType;
    }

    public StringProperty addressProperty() {
        return address;
    }

    public BooleanProperty connectedProperty() {
        return connected;
    }

    public void setConnected(String connectionInfo) {
        this.status.set("Подключено: " + connectionInfo);
        this.connected.set(true);
    }

    public void setConnectionType(String type) {
        this.connectionType.set(type);
    }

    public void setDisconnected(String reason) {
        this.status.set("Отключено" + (reason.isEmpty() ? "" : " (" + reason + ")"));
        this.connectionType.set("");
        this.address.set("");
        this.connected.set(false);
    }

    public void setConnecting() {
        this.status.set("Подключение...");
    }
}
