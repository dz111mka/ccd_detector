package by.spectrometer.controller;

import by.spectrometer.model.ConnectionState;
import by.spectrometer.model.ConnectionType;
import by.spectrometer.model.SpectrumData;
import by.spectrometer.service.ConnectionService;
import by.spectrometer.service.LogService;
import by.spectrometer.service.SerialConnectionService;
import by.spectrometer.service.WebSocketConnectionService;
import by.spectrometer.ui.SpectrumChart;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import com.fazecast.jSerialComm.SerialPort;

import java.util.prefs.Preferences;

public class SpectrometerController {

    // ────────────────────────────────────────────────────────────────
    // Поля (модели и состояние)
    // ────────────────────────────────────────────────────────────────
    private final VBox view = new VBox(15);
    private final SpectrumData data = new SpectrumData();
    private final ConnectionState connState = new ConnectionState();
    private ConnectionService connectionService;

    private ConnectionType currentConnectionType = ConnectionType.WEBSOCKET;

    // ────────────────────────────────────────────────────────────────
    // UI-компоненты (всегда final)
    // ────────────────────────────────────────────────────────────────
    private final ComboBox<ConnectionType> cbConnectionType = new ComboBox<>();
    private final TextField tfAddress = new TextField();
    private final ComboBox<String> cbSerialPorts = new ComboBox<>();
    private final Button btnRefreshPorts = new Button("Обновить");
    private final Button btnConnect = new Button();
    private final Label lblStatus = new Label();
    private final CheckBox cbAbs = new CheckBox("Показывать Absorbance");
    private final SpectrumChart chart;
    private final ListView<String> logView = new ListView<>();

    // Кнопки управления (создаём здесь, чтобы не плодить локальные переменные)
    private Button btnDark;
    private Button btnRef;
    private Button btnLive;
    private Button btnCapture;

    // ────────────────────────────────────────────────────────────────
    // Конструктор
    // ────────────────────────────────────────────────────────────────
    public SpectrometerController() {
        chart = new SpectrumChart(data);
        initializeUIComponents();
        setupBindings();
        setupEventHandlers();
        buildLayout();
        loadLastConnection();
        refreshSerialPorts();
    }

    // ────────────────────────────────────────────────────────────────
    // Инициализация свойств компонентов
    // ────────────────────────────────────────────────────────────────
    private void initializeUIComponents() {
        // Connection type
        cbConnectionType.setItems(FXCollections.observableArrayList(ConnectionType.values()));
        cbConnectionType.setValue(ConnectionType.WEBSOCKET);

        // Address field
        tfAddress.setPrefWidth(200);
        tfAddress.setPromptText("ws://IP:порт (например: 192.168.1.77:81)");

        // Serial ports
        cbSerialPorts.setPrefWidth(150);
        cbSerialPorts.setVisible(false);

        // Log view
        logView.setItems(LogService.getLogs());
        logView.setPrefHeight(180);
        logView.setStyle("""
            -fx-font-family: Consolas;
            -fx-font-size: 12;
        """);

        // Chart
        chart.setAnimated(false);
        chart.setCreateSymbols(false);

        // ── Кнопки управления ── (переносим сюда)
        btnDark    = new Button("Тёмный ток");
        btnRef     = new Button("Белая опора");
        btnLive    = new Button("Live ON");
        btnCapture = new Button("Захватить");
    }

    // ────────────────────────────────────────────────────────────────
    // Привязки свойств (bindings)
    // ────────────────────────────────────────────────────────────────
    private void setupBindings() {
        lblStatus.textProperty().bind(connState.statusProperty());

        btnConnect.textProperty().bind(
                connState.connectedProperty()
                        .map(connected -> connected ? "Отключиться" : "Подключиться")
        );

        // Автоскролл логов
        LogService.getLogs().addListener((ListChangeListener<String>) change ->
                logView.scrollTo(LogService.getLogs().size() - 1));
    }

    // ────────────────────────────────────────────────────────────────
    // События и обработчики
    // ────────────────────────────────────────────────────────────────
    private void setupEventHandlers() {
        cbConnectionType.setOnAction(e -> onConnectionTypeChanged());

        btnRefreshPorts.setOnAction(e -> refreshSerialPorts());

        btnConnect.setOnAction(e -> toggleConnection());

        cbAbs.setOnAction(e -> {
            chart.setShowAbsorbance(cbAbs.isSelected());
            chart.redraw(data);
        });

        // Кнопки управления
        btnDark.setOnAction(e -> sendCommand("DARK"));
        btnRef.setOnAction(e -> sendCommand("REF"));
        btnCapture.setOnAction(e -> sendCommand("CAPTURE"));

        btnLive.setOnAction(e -> {
            boolean turnOn = btnLive.getText().contains("ON");
            btnLive.setText(turnOn ? "Live OFF" : "Live ON");
            sendCommand(turnOn ? "LIVE ON" : "LIVE OFF");
        });

        logView.setOnKeyPressed(event -> {
            if (event.isControlDown() && event.getCode() == KeyCode.C) {
                ObservableList<String> selected = logView.getSelectionModel().getSelectedItems();
                if (!selected.isEmpty()) {
                    String text = String.join("\n", selected);
                    Clipboard clipboard = Clipboard.getSystemClipboard();
                    ClipboardContent content = new ClipboardContent();
                    content.putString(text);
                    clipboard.setContent(content);
                    event.consume(); // чтобы не передавалось дальше
                }
            }
        });
    }

    // ────────────────────────────────────────────────────────────────
    // Сборка layout-ов
    // ────────────────────────────────────────────────────────────────
    private void buildLayout() {
        // Панель подключения
        HBox connectionPanel = new HBox(10);
        connectionPanel.setAlignment(Pos.CENTER_LEFT);
        connectionPanel.getChildren().addAll(
                new Label("Тип:"), cbConnectionType,
                new Label("Адрес:"), tfAddress,
                cbSerialPorts, btnRefreshPorts,
                btnConnect, lblStatus
        );

        // Панель управления
        HBox controls = new HBox(20,
                btnDark = new Button("Тёмный ток"),
                btnRef  = new Button("Белая опора"),
                btnLive = new Button("Live ON"),
                btnCapture = new Button("Захватить"),
                cbAbs
        );

        // Основной контейнер
        view.setPadding(new Insets(20));
        view.setStyle("-fx-background-color: #f4f4f4;");

        view.getChildren().addAll(
                connectionPanel,
                controls,
                chart,
                new Label("Логи:"),
                logView
        );
    }

    // ────────────────────────────────────────────────────────────────
    // Логика подключения / отключения / команд (остаётся почти без изменений)
    // ────────────────────────────────────────────────────────────────
    private void onConnectionTypeChanged() {
        currentConnectionType = cbConnectionType.getValue();
        boolean isSerial = currentConnectionType == ConnectionType.SERIAL;

        cbSerialPorts.setVisible(isSerial);
        tfAddress.setVisible(!isSerial);

        tfAddress.setPromptText(isSerial
                ? "Выберите COM порт"
                : "ws://IP:порт (например: 192.168.1.77:81)");
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
        String address = switch (currentConnectionType) {
            case SERIAL -> {
                String selected = cbSerialPorts.getValue();
                if (selected == null) {
                    lblStatus.setText("Выберите COM порт");
                    yield null;
                }
                yield selected.split(" ")[0];
            }
            case WEBSOCKET -> {
                String text = tfAddress.getText().trim();
                yield text.contains("://") ? text : "ws://" + text;
            }
        };

        if (address == null || address.isEmpty()) return;

        connectionService = switch (currentConnectionType) {
            case SERIAL    -> new SerialConnectionService(data, connState, this::updateChart);
            case WEBSOCKET -> new WebSocketConnectionService(data, connState, this::updateChart);
        };

        connectionService.connect(address);
        saveLastConnection();
    }

    private void disconnect() {
        if (connectionService != null) {
            connectionService.disconnect();
        }
    }

    private void sendCommand(String command) {
        LogService.log("CMD ▶ " + command);
        if (connectionService != null && connectionService.isConnected()) {
            connectionService.sendCommand(command);
        }
    }

    private void updateChart() {
        chart.redraw(data);
    }

    private void loadLastConnection() {
        Preferences p = Preferences.userNodeForPackage(getClass());
        String saved = p.get("connectionType", "WEBSOCKET");
        ConnectionType type = ConnectionType.valueOf(saved);
        cbConnectionType.setValue(type);

        if (type == ConnectionType.WEBSOCKET) {
            tfAddress.setText(p.get("wsAddress", "192.168.1.77:81"));
        }
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