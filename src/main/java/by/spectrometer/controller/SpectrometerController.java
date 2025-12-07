package by.spectrometer.controller;

import by.spectrometer.model.ConnectionState;
import by.spectrometer.model.ConnectionType;
import by.spectrometer.model.SpectrumData;
import by.spectrometer.service.ConnectionService;
import by.spectrometer.service.SerialConnectionService;
import by.spectrometer.service.WebSocketConnectionService;
import by.spectrometer.ui.SpectrumChart;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import com.fazecast.jSerialComm.SerialPort;

import java.util.prefs.Preferences;

public class SpectrometerController {

    private final VBox view = new VBox(15);
    private final SpectrumData data = new SpectrumData();
    private final ConnectionState connState = new ConnectionState();
    private ConnectionService connectionService;

    private ConnectionType currentConnectionType = ConnectionType.WEBSOCKET;

    // UI элементы
    private final ComboBox<ConnectionType> cbConnectionType = new ComboBox<>();
    private final TextField tfAddress = new TextField();
    private final ComboBox<String> cbSerialPorts = new ComboBox<>();
    private final Button btnRefreshPorts = new Button("Обновить");
    private final Button btnConnect = new Button();
    private final Label lblStatus = new Label();
    private final CheckBox cbAbs = new CheckBox("Показывать Absorbance");
    private final SpectrumChart chart;

    public SpectrometerController() {
        chart = new SpectrumChart(data);
        setupUI();
        loadLastConnection();
        refreshSerialPorts();
    }

    private void setupUI() {
        // Настройка выбора типа подключения
        cbConnectionType.setItems(FXCollections.observableArrayList(ConnectionType.values()));
        cbConnectionType.setValue(ConnectionType.WEBSOCKET);
        cbConnectionType.setOnAction(e -> onConnectionTypeChanged());

        // Настройка полей ввода
        tfAddress.setPrefWidth(200);
        tfAddress.setPromptText("IP:порт или COM порт");

        // Настройка выбора COM портов
        cbSerialPorts.setPrefWidth(150);
        cbSerialPorts.setVisible(false);
        btnRefreshPorts.setOnAction(e -> refreshSerialPorts());

        // Кнопка подключения
        btnConnect.setOnAction(e -> toggleConnection());

        // Checkbox для absorbance
        cbAbs.setOnAction(e -> {
            chart.setShowAbsorbance(cbAbs.isSelected());
            chart.redraw(data);
        });

        // Биндинг свойств
        lblStatus.textProperty().bind(connState.statusProperty());
        btnConnect.textProperty().bind(
                connState.connectedProperty().map(c -> c ? "Отключиться" : "Подключиться")
        );

        // Панель подключения
        HBox connectionPanel = new HBox(10);
        connectionPanel.setAlignment(Pos.CENTER_LEFT);
        connectionPanel.getChildren().addAll(
                new Label("Тип:"), cbConnectionType,
                new Label("Адрес:"), tfAddress,
                cbSerialPorts, btnRefreshPorts, btnConnect, lblStatus
        );

        // Панель управления
        Button btnDark = new Button("Тёмный ток");
        Button btnRef = new Button("Белая опора");
        Button btnLive = new Button("Live ON");
        Button btnCapture = new Button("Захватить");

        btnDark.setOnAction(e -> sendCommand("{\"cmd\":\"dark\"}"));
        btnRef.setOnAction(e -> sendCommand("{\"cmd\":\"ref\"}"));
        btnLive.setOnAction(e -> {
            boolean on = btnLive.getText().contains("ON");
            btnLive.setText(on ? "Live OFF" : "Live ON");
            sendCommand(on ? "{\"cmd\":\"live\",\"on\":true}" : "{\"cmd\":\"live\",\"on\":false}");
        });
        btnCapture.setOnAction(e -> sendCommand("{\"cmd\":\"capture\"}"));

        HBox controls = new HBox(20, btnDark, btnRef, btnLive, btnCapture, cbAbs);

        // Основной layout
        view.setPadding(new Insets(20));
        view.getChildren().addAll(connectionPanel, controls, chart);
        view.setStyle("-fx-background-color: #f4f4f4;");
    }

    private void onConnectionTypeChanged() {
        currentConnectionType = cbConnectionType.getValue();
        boolean isSerial = currentConnectionType == ConnectionType.SERIAL;

        cbSerialPorts.setVisible(isSerial);
        tfAddress.setVisible(!isSerial);

        if (isSerial) {
            tfAddress.setPromptText("Выберите COM порт");
        } else {
            tfAddress.setPromptText("ws://IP:порт (например: 192.168.1.77:81)");
        }
    }

    private void refreshSerialPorts() {
        cbSerialPorts.getItems().clear();
        SerialPort[] ports = SerialPort.getCommPorts();
        for (SerialPort port : ports) {
            cbSerialPorts.getItems().add(port.getSystemPortName() + " - " + port.getDescriptivePortName());
        }
        if (!cbSerialPorts.getItems().isEmpty()) {
            cbSerialPorts.setValue(cbSerialPorts.getItems().get(0));
        }
    }

    private void toggleConnection() {
        if (connState.connectedProperty().get()) {
            disconnect();
        } else {
            connect();
        }
    }

    private void connect() {
        String address;

        if (currentConnectionType == ConnectionType.SERIAL) {
            address = cbSerialPorts.getValue();
            if (address != null) {
                // Извлекаем только имя порта (до первого пробела)
                address = address.split(" ")[0];
            } else {
                lblStatus.setText("Выберите COM порт");
                return;
            }
            connectionService = new SerialConnectionService(data, connState, this::updateChart);
        } else {
            address = tfAddress.getText().trim();
            if (!address.contains("://")) {
                address = "ws://" + address;
            }
            connectionService = new WebSocketConnectionService(data, connState, this::updateChart);
        }

        if (address != null && !address.isEmpty()) {
            connectionService.connect(address);
            saveLastConnection();
        }
    }

    private void disconnect() {
        if (connectionService != null) {
            connectionService.disconnect();
        }
    }

    private void sendCommand(String command) {
        if (connectionService != null && connectionService.isConnected()) {
            connectionService.sendCommand(command);
        }
    }

    private void updateChart() {
        chart.redraw(data);
    }

    private void loadLastConnection() {
        Preferences p = Preferences.userNodeForPackage(getClass());
        ConnectionType savedType = ConnectionType.valueOf(p.get("connectionType", "WEBSOCKET"));
        cbConnectionType.setValue(savedType);

        if (savedType == ConnectionType.WEBSOCKET) {
            tfAddress.setText(p.get("wsAddress", "192.168.1.77:81"));
        }
        // Для Serial портов обычно не сохраняем, так как они могут меняться
    }

    private void saveLastConnection() {
        Preferences p = Preferences.userNodeForPackage(getClass());
        p.put("connectionType", currentConnectionType.name());

        if (currentConnectionType == ConnectionType.WEBSOCKET) {
            p.put("wsAddress", tfAddress.getText());
        }
    }

    public VBox getView() {
        return view;
    }
}