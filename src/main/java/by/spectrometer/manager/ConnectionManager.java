package by.spectrometer.manager;

import by.spectrometer.controller.SpectrometerController;
import by.spectrometer.model.ConnectionState;
import by.spectrometer.model.ConnectionType;
import by.spectrometer.model.SpectrumData;
import by.spectrometer.service.ConnectionService;
import by.spectrometer.service.LogService;
import by.spectrometer.service.SerialConnectionService;
import by.spectrometer.service.WebSocketConnectionService;
import com.fazecast.jSerialComm.SerialPort;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

import java.util.Arrays;
import java.util.prefs.Preferences;

public class ConnectionManager {

    private final SpectrometerController controller;
    private final SpectrumData data;
    private final ConnectionState connState;

    private ConnectionService connectionService;
    private ConnectionType currentConnectionType = ConnectionType.SERIAL;

    public ConnectionManager(SpectrometerController controller, SpectrumData data, ConnectionState connState) {
        this.controller = controller;
        this.data = data;
        this.connState = connState;
    }

    public void refreshPorts(ComboBox<String> cbSerialPorts) {
        cbSerialPorts.getItems().clear();
        Arrays.stream(SerialPort.getCommPorts())
                .forEach(port -> cbSerialPorts.getItems().add(
                        port.getSystemPortName() + " - " + port.getDescriptivePortName()
                ));

        if (!cbSerialPorts.getItems().isEmpty()) {
            cbSerialPorts.setValue(cbSerialPorts.getItems().getFirst());
        }
    }

    public void handleConnectionTypeChange(ComboBox<ConnectionType> cbConnectionType,
                                           ComboBox<String> cbSerialPorts, TextField tfAddress) {
        currentConnectionType = cbConnectionType.getValue();
        boolean isSerial = currentConnectionType == ConnectionType.SERIAL;

        cbSerialPorts.setVisible(isSerial);
        tfAddress.setVisible(!isSerial);

        String prompt = isSerial ? "Выберите COM порт"
                : "ws://IP:порт (например: 192.168.1.77:81)";
        tfAddress.setPromptText(prompt);
    }

    public void toggleConnection(ComboBox<String> cbSerialPorts, TextField tfAddress, Label lblStatus) {
        if (connState.connectedProperty().get()) {
            disconnect();
        } else {
            connect(cbSerialPorts, tfAddress, lblStatus);
        }
    }

    private void connect(ComboBox<String> cbSerialPorts, TextField tfAddress, Label lblStatus) {
        String address = getConnectionAddress(cbSerialPorts, tfAddress, lblStatus);
        if (address == null || address.isEmpty()) return;

        connectionService = createConnectionService();
        connectionService.connect(address);
        saveConnectionConfiguration();
    }

    private String getConnectionAddress(ComboBox<String> cbSerialPorts, TextField tfAddress, Label lblStatus) {
        return switch (currentConnectionType) {
            case SERIAL -> getSelectedSerialPort(cbSerialPorts, lblStatus);
            case WEBSOCKET -> getWebSocketAddress(tfAddress);
        };
    }

    public String getSelectedSerialPort(ComboBox<String> cbSerialPorts, Label lblStatus) {
        String selected = cbSerialPorts.getValue();
        if (selected == null) {
            lblStatus.setText("Выберите COM порт");
            return null;
        }
        return selected.split(" ")[0];
    }

    private String getWebSocketAddress(TextField tfAddress) {
        String text = tfAddress.getText().trim();
        return text.contains("://") ? text : "ws://" + text;
    }

    private ConnectionService createConnectionService() {
        return switch (currentConnectionType) {
            case SERIAL -> new SerialConnectionService(data, connState, controller::updateChart);
            case WEBSOCKET -> new WebSocketConnectionService(data, connState, controller::updateChart);
        };
    }

    private void disconnect() {
        if (connectionService != null) {
            connectionService.disconnect();
        }
    }

    public void sendCommand(String command) {
        LogService.log("CMD ▶ " + command);
        if (connectionService != null && connectionService.isConnected()) {
            connectionService.sendCommand(command);
        }
    }

    public boolean isConnected() {
        return connectionService != null && connectionService.isConnected();
    }

    public ConnectionType getCurrentConnectionType() {
        return currentConnectionType;
    }

    public void loadConfiguration(ComboBox<ConnectionType> cbConnectionType, TextField tfAddress) {
        Preferences prefs = Preferences.userNodeForPackage(getClass());
        ConnectionType type = ConnectionType.valueOf(
                prefs.get("connectionType", "WEBSOCKET")
        );
        cbConnectionType.setValue(type);

        if (type == ConnectionType.WEBSOCKET) {
            tfAddress.setText(prefs.get("wsAddress", "192.168.1.77:81"));
        }
    }

    public void saveConnectionConfiguration() {
        Preferences prefs = Preferences.userNodeForPackage(getClass());
        prefs.put("connectionType", currentConnectionType.name());

        if (currentConnectionType == ConnectionType.WEBSOCKET) {
            // tfAddress is not directly accessible here - would need to pass or store
        }
    }
}