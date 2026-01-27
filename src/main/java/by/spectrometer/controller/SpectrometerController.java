package by.spectrometer.controller;

import by.spectrometer.model.ConnectionState;
import by.spectrometer.model.ConnectionType;
import by.spectrometer.model.SpectrumData;
import by.spectrometer.service.ConnectionService;
import by.spectrometer.service.LogService;
import by.spectrometer.service.SerialConnectionService;
import by.spectrometer.service.WebSocketConnectionService;
import by.spectrometer.ui.SpectrumChart;
import by.spectrometer.util.Constants;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import com.fazecast.jSerialComm.SerialPort;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.DoubleSummaryStatistics;
import java.util.List;
import java.util.prefs.Preferences;

public class SpectrometerController {

    // ────────────────────────────────────────────────────────────────
    // Модели данных
    // ────────────────────────────────────────────────
    private final SpectrumData data = new SpectrumData();
    private final ConnectionState connState = new ConnectionState();
    private final BooleanProperty arduinoConnected = new SimpleBooleanProperty(false);
    private final BooleanProperty reflectionMode = new SimpleBooleanProperty(false);

    // ────────────────────────────────────────────────────────────────
    // UI компоненты
    // ────────────────────────────────────────────────
    private final VBox view = new VBox(15);
    private final SpectrumChart chart;
    private final ListView<String> logView = new ListView<>();

    // Компоненты подключения спектрометра
    private final ComboBox<ConnectionType> cbConnectionType = new ComboBox<>();
    private final TextField tfAddress = new TextField();
    private final ComboBox<String> cbSerialPorts = new ComboBox<>();
    private final Button btnRefreshPorts = new Button("Обновить");
    private final Button btnConnect = new Button();
    private final Label lblStatus = new Label();

    // Кнопки управления измерением
    private final Button btnDark = new Button("Тёмный ток");
    private final Button btnRef = new Button("Белая опора");
    private final Button btnCapture = new Button("Захватить");
    private final Button btnMinima = new Button("Минимумы");
    private final Button btnSmooth = new Button("Сгладить");

    // Компоненты управления Arduino
    private final ComboBox<String> cbArduinoPort = new ComboBox<>();
    private final Button btnConnectArduino = new Button("Подключить Arduino");
    private final Button btnMode = new Button("Перейти в режим ОТРАЖЕНИЯ");
    private final Label lblMotor1Pos = new Label("Мотор 1: 0°");
    private final Label lblMotor2Pos = new Label("Мотор 2: 0°");

    // Компоненты тонкой настройки
    private final VBox reflectionControls = new VBox(10);
    private final Slider sliderFineAngle = new Slider(Constants.FINE_ADJUSTMENT_MIN, Constants.FINE_ADJUSTMENT_MAX, 0);
    private final Label lblFineAngle = new Label("0°");
    private final Button btnApplyFine = new Button("Применить подстройку");

    // ────────────────────────────────────────────────────────────────
    // Внутреннее состояние
    // ────────────────────────────────────────────────
    private ConnectionService connectionService;
    private ConnectionType currentConnectionType = ConnectionType.SERIAL;
    private SerialPort arduinoPort;
    private long lastRedraw = 0;
    private int currentPosMotor1 = 0;
    private int currentPosMotor2 = 0;

    // ────────────────────────────────────────────────────────────────
    // Конструктор и инициализация
    // ────────────────────────────────────────────────
    public SpectrometerController() {
        chart = new SpectrumChart(data);
        initializeUI();
        setupBindings();
        setupEventHandlers();
        buildLayout();
        loadConfiguration();
        refreshPorts();
        setupReflectionControls();
    }

    // ────────────────────────────────────────────────────────────────
    // Инициализация UI
    // ────────────────────────────────────────────────
    private void initializeUI() {
        initializeConnectionUI();
        initializeMeasurementUI();
        initializeArduinoUI();
        initializeLogView();
        configureVisualStyles();
    }

    private void initializeConnectionUI() {
        cbConnectionType.setItems(FXCollections.observableArrayList(ConnectionType.values()));
        cbConnectionType.setValue(ConnectionType.SERIAL);

        tfAddress.setPrefWidth(200);
        tfAddress.setPromptText("ws://IP:порт (например: 192.168.1.77:81)");
        tfAddress.setVisible(false);

        cbSerialPorts.setPrefWidth(150);
        cbSerialPorts.setVisible(true);
    }

    private void initializeMeasurementUI() {
        chart.setAnimated(false);
        chart.setCreateSymbols(false);
    }

    private void initializeArduinoUI() {
        cbArduinoPort.setPromptText("Выберите порт Arduino");
        btnMode.setDisable(true);
        lblMotor1Pos.setStyle("-fx-font-weight: bold;");
        lblMotor2Pos.setStyle("-fx-font-weight: bold;");
    }

    private void initializeLogView() {
        logView.setItems(LogService.getLogs());
        logView.setPrefHeight(180);
        logView.setStyle("""
            -fx-font-family: Consolas;
            -fx-font-size: 12;
        """);
    }

    private void setupReflectionControls() {
        sliderFineAngle.setMajorTickUnit(15);
        sliderFineAngle.setMinorTickCount(3);
        sliderFineAngle.setShowTickMarks(true);
        sliderFineAngle.setShowTickLabels(true);
        reflectionControls.setVisible(false);

        HBox fineAdjustmentBox = new HBox(10, sliderFineAngle, lblFineAngle, btnApplyFine);
        reflectionControls.getChildren().addAll(
                new Label("Тонкая подстройка угла отражения:"),
                fineAdjustmentBox
        );
    }

    private void configureVisualStyles() {
        view.setPadding(new Insets(20));
        view.setStyle("-fx-background-color: #f4f4f4;");

        if (!arduinoConnected.get()) {
            btnMode.setStyle("-fx-opacity: 0.6;");
        }
    }

    // ────────────────────────────────────────────────────────────────
    // Настройка привязок
    // ────────────────────────────────────────────────
    private void setupBindings() {
        bindConnectionStatus();
        bindFineAdjustmentSlider();
        bindLogAutoScroll();
    }

    private void bindConnectionStatus() {
        lblStatus.textProperty().bind(connState.statusProperty());
        btnConnect.textProperty().bind(
                connState.connectedProperty()
                        .map(connected -> connected ? "Отключиться" : "Подключиться")
        );
    }

    private void bindFineAdjustmentSlider() {
        sliderFineAngle.valueProperty().addListener((obs, old, val) ->
                lblFineAngle.setText(String.format("%+.0f°", val.doubleValue()))
        );
    }

    private void bindLogAutoScroll() {
        LogService.getLogs().addListener((ListChangeListener<String>) change ->
                logView.scrollTo(LogService.getLogs().size() - 1)
        );
    }

    // ────────────────────────────────────────────────────────────────
    // Обработчики событий
    // ────────────────────────────────────────────────
    private void setupEventHandlers() {
        setupConnectionEventHandlers();
        setupMeasurementEventHandlers();
        setupArduinoEventHandlers();
        setupLogEventHandlers();
        setupModeEventHandlers();
    }

    private void setupConnectionEventHandlers() {
        cbConnectionType.setOnAction(e -> handleConnectionTypeChange());
        btnRefreshPorts.setOnAction(e -> refreshPorts());
        btnConnect.setOnAction(e -> toggleConnection());
    }

    private void setupMeasurementEventHandlers() {
        btnDark.setOnAction(e -> sendCommand("DARK"));
        btnRef.setOnAction(e -> sendCommand("REF"));
        btnCapture.setOnAction(e -> toggleCaptureMode());
        btnSmooth.setOnAction(e -> applySmoothing());
        btnMinima.setOnAction(e -> findMinima());
    }

    private void setupArduinoEventHandlers() {
        btnConnectArduino.setOnAction(e -> toggleArduinoConnection());
        btnApplyFine.setOnAction(e -> applyFineAdjustment());
    }

    private void setupLogEventHandlers() {
        logView.setOnKeyPressed(this::handleLogKeyPress);
    }

    private void setupModeEventHandlers() {
        btnMode.setOnAction(e -> toggleMeasurementMode());
        reflectionMode.addListener((obs, wasReflection, isNowReflection) ->
                handleMeasurementModeChange(isNowReflection)
        );
    }

    // ────────────────────────────────────────────────────────────────
    // Построение layout
    // ────────────────────────────────────────────────
    private void buildLayout() {
        view.getChildren().addAll(
                buildConnectionPanel(),
                buildMeasurementControls(),
                chart,
                new Label("Логи:"),
                logView,
                buildArduinoPanel(),
                buildPositionDisplay()
        );
    }

    private HBox buildConnectionPanel() {
        HBox panel = new HBox(10);
        panel.setAlignment(Pos.CENTER_LEFT);
        panel.getChildren().addAll(
                new Label("Тип:"), cbConnectionType,
                new Label("Адрес:"), tfAddress,
                cbSerialPorts, btnRefreshPorts,
                btnConnect, lblStatus
        );
        return panel;
    }

    private HBox buildMeasurementControls() {
        return new HBox(20, btnDark, btnRef, btnCapture, btnSmooth, btnMinima);
    }

    private VBox buildArduinoPanel() {
        HBox connectionBox = new HBox(10, cbArduinoPort, btnConnectArduino, btnMode);
        VBox panel = new VBox(10,
                new Label("Управление сервоприводом"),
                connectionBox,
                reflectionControls
        );
        return panel;
    }

    private VBox buildPositionDisplay() {
        VBox display = new VBox(5, lblMotor1Pos, lblMotor2Pos);
        display.setPadding(new Insets(10, 0, 0, 0));
        return display;
    }

    // ────────────────────────────────────────────────────────────────
    // Основная логика
    // ────────────────────────────────────────────────
    private void handleConnectionTypeChange() {
        currentConnectionType = cbConnectionType.getValue();
        boolean isSerial = currentConnectionType == ConnectionType.SERIAL;

        cbSerialPorts.setVisible(isSerial);
        tfAddress.setVisible(!isSerial);

        String prompt = isSerial ? "Выберите COM порт"
                : "ws://IP:порт (например: 192.168.1.77:81)";
        tfAddress.setPromptText(prompt);
    }

    private void refreshPorts() {
        refreshSpectrometerPorts();
        refreshArduinoPorts();
    }

    private void refreshSpectrometerPorts() {
        cbSerialPorts.getItems().clear();
        Arrays.stream(SerialPort.getCommPorts())
                .forEach(port -> cbSerialPorts.getItems().add(
                        port.getSystemPortName() + " - " + port.getDescriptivePortName()
                ));

        if (!cbSerialPorts.getItems().isEmpty()) {
            cbSerialPorts.setValue(cbSerialPorts.getItems().getFirst());
        }
    }

    private void refreshArduinoPorts() {
        cbArduinoPort.getItems().clear();
        Arrays.stream(SerialPort.getCommPorts())
                .forEach(port -> cbArduinoPort.getItems().add(
                        port.getSystemPortName() + " - " + port.getDescriptivePortName()
                ));

        if (!cbArduinoPort.getItems().isEmpty()) {
            cbArduinoPort.setValue(cbArduinoPort.getItems().getFirst());
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
        String address = getConnectionAddress();
        if (address == null || address.isEmpty()) return;

        connectionService = createConnectionService();
        connectionService.connect(address);
        saveConnectionConfiguration();
    }

    private String getConnectionAddress() {
        return switch (currentConnectionType) {
            case SERIAL -> getSelectedSerialPort();
            case WEBSOCKET -> getWebSocketAddress();
        };
    }

    private String getSelectedSerialPort() {
        String selected = cbSerialPorts.getValue();
        if (selected == null) {
            lblStatus.setText("Выберите COM порт");
            return null;
        }
        return selected.split(" ")[0];
    }

    private String getWebSocketAddress() {
        String text = tfAddress.getText().trim();
        return text.contains("://") ? text : "ws://" + text;
    }

    private ConnectionService createConnectionService() {
        return switch (currentConnectionType) {
            case SERIAL -> new SerialConnectionService(data, connState, this::updateChart);
            case WEBSOCKET -> new WebSocketConnectionService(data, connState, this::updateChart);
        };
    }

    private void disconnect() {
        if (connectionService != null) {
            connectionService.disconnect();
        }
    }

    private void toggleCaptureMode() {
        if (!chart.isFrozen()) {
            chart.capture(data);
            btnCapture.setText("Live ON");
        } else {
            chart.release();
            btnCapture.setText("Захватить");
        }
    }

    private void applySmoothing() {
        if (!chart.isFrozen()) {
            LogService.log("Smooth is only available in Capture mode.");
            return;
        }
        chart.smooth(Constants.SMOOTHING_WINDOW_SIZE);
    }

    private void findMinima() {
        if (!validateMinimaSearch()) return;

        LogService.log("Finding minimums on a captured chart...");
        logCapturedDataRange();

        chart.clearMinima();
        List<Integer> minima = chart.findLocalMinima(50, 3000);

        LogService.log("Minimums found: " + minima.size());
        if (minima.isEmpty()) {
            searchWithDifferentThresholds();
        } else {
            logMinimaDetails(minima);
        }
    }

    private boolean validateMinimaSearch() {
        if (!chart.isFrozen()) {
            LogService.log("Minimum search is only available in Capture mode.");
            LogService.log("The chart is frozen: " + chart.isFrozen());
            LogService.log("capturedY: " + (chart.getCapturedY() != null ? "не null" : "null"));
            return false;
        }
        return true;
    }

    private void logCapturedDataRange() {
        if (chart.getCapturedY() != null) {
            DoubleSummaryStatistics stats = Arrays.stream(chart.getCapturedY())
                    .summaryStatistics();
            LogService.log(String.format("capturedY range: min=%.2f, max=%.2f",
                    stats.getMin(), stats.getMax()));
        }
    }

    private void searchWithDifferentThresholds() {
        double[] thresholds = {1000, 1500, 2000, 2500, 3000, 3500};
        for (double threshold : thresholds) {
            List<Integer> minima = chart.findLocalMinima(50, threshold);
            LogService.log(String.format("  Threshold %.0f: found %d minima", threshold, minima.size()));
            if (!minima.isEmpty()) {
                chart.clearMinima();
                chart.findLocalMinima(50, threshold);
                break;
            }
        }
    }

    private void logMinimaDetails(List<Integer> minima) {
        for (int idx : minima) {
            double yValue = chart.getCapturedY() != null ? chart.getCapturedY()[idx] : 0;
            LogService.log(String.format("  Pixel %d: Y=%.2f, RawValue=%.2f",
                    idx, yValue, 4095 - yValue));
        }
    }

    private void updateChart() {
        if (chart.isFrozen() || !shouldRedraw()) return;

        lastRedraw = System.currentTimeMillis();
        chart.redraw(data);
    }

    private boolean shouldRedraw() {
        return System.currentTimeMillis() - lastRedraw >= Constants.REDRAW_INTERVAL_MS;
    }

    // ────────────────────────────────────────────────────────────────
    // Конфигурация
    // ────────────────────────────────────────────────
    private void loadConfiguration() {
        Preferences prefs = Preferences.userNodeForPackage(getClass());
        ConnectionType type = ConnectionType.valueOf(
                prefs.get("connectionType", "WEBSOCKET")
        );
        cbConnectionType.setValue(type);

        if (type == ConnectionType.WEBSOCKET) {
            tfAddress.setText(prefs.get("wsAddress", "192.168.1.77:81"));
        }
    }

    private void saveConnectionConfiguration() {
        Preferences prefs = Preferences.userNodeForPackage(getClass());
        prefs.put("connectionType", currentConnectionType.name());

        if (currentConnectionType == ConnectionType.WEBSOCKET) {
            prefs.put("wsAddress", tfAddress.getText());
        }
    }

    // ────────────────────────────────────────────────────────────────
    // Arduino управление
    // ────────────────────────────────────────────────
    private void toggleArduinoConnection() {
        if (arduinoConnected.get()) {
            disconnectArduino();
        } else {
            connectToArduino();
        }
        updateArduinoModeButton();
    }

    private void connectToArduino() {
        String selected = cbArduinoPort.getValue();
        if (selected == null || selected.isEmpty()) {
            LogService.log("Select port Arduino");
            return;
        }

        String portName = selected.split(" - ")[0].trim();
        arduinoPort = SerialPort.getCommPort(portName);
        arduinoPort.setBaudRate(115200);
        arduinoPort.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 100, 100);

        if (arduinoPort.openPort()) {
            arduinoConnected.set(true);
            btnConnectArduino.setText("Отключить Arduino");
            LogService.log("Arduino is connected on " + portName + " @ 115200");
            moveToZeroPosition();
            updateArduinoModeButton();
        } else {
            LogService.log("Error opening port " + portName);
        }
    }

    private void disconnectArduino() {
        if (arduinoPort != null && arduinoPort.isOpen()) {
            arduinoPort.closePort();
        }
        arduinoConnected.set(false);
        btnConnectArduino.setText("Подключить Arduino");
        LogService.log("Arduino is disconnected");
    }

    private void moveToZeroPosition() {
        sendStepperCommand("HOME");
        currentPosMotor1 = 0;
        currentPosMotor2 = 0;
        LogService.log("The engines have been moved to the zero position. (0°, 0°)");
        updatePositionDisplay();
    }

    private void updatePositionDisplay() {
        Platform.runLater(() -> {
            lblMotor1Pos.setText("Stepper 1: " + currentPosMotor1 + "°");
            lblMotor2Pos.setText("Stepper 2: " + currentPosMotor2 + "°");
        });
    }

    private void toggleMeasurementMode() {
        boolean newMode = !reflectionMode.get();
        reflectionMode.set(newMode);
        LogService.log("Switching to " + (newMode ? "REFLECTION" : "TRANSMISSION") + " mode");
    }

    private void handleMeasurementModeChange(boolean isReflectionMode) {
        updateModeUI(isReflectionMode);
        if (isReflectionMode) {
            moveToReflectionPosition();
        } else {
            moveToTransmissionPosition();
        }
    }

    private void updateModeUI(boolean isReflectionMode) {
        reflectionControls.setVisible(isReflectionMode);
        btnMode.setText(isReflectionMode
                ? "Перейти в режим ПРОПУСКАНИЕ"
                : "Перейти в режим ОТРАЖЕНИЕ");

        if (!isReflectionMode) {
            sliderFineAngle.setValue(0);
        }
    }

    private void moveToTransmissionPosition() {
        sendStepperCommand("HOME");
        currentPosMotor1 = Constants.TRANSMISSION_POS_MOTOR1;
        currentPosMotor2 = Constants.TRANSMISSION_POS_MOTOR2;
        updatePositionDisplay();
        LogService.log("selected TRANSFERENCE mode");
    }

    private void moveToReflectionPosition() {
        int delta1 = Constants.REFLECTION_BASE_POS_MOTOR1 - currentPosMotor1;
        int delta2 = Constants.REFLECTION_BASE_POS_MOTOR2 - currentPosMotor2;

        sendStepperCommand("MOVE 1 " + delta1);
        sendStepperCommand("MOVE 2 " + delta2);

        currentPosMotor1 = Constants.REFLECTION_BASE_POS_MOTOR1;
        currentPosMotor2 = Constants.REFLECTION_BASE_POS_MOTOR2;
        updatePositionDisplay();
        LogService.log("selected REFLECTION mode (90°, 135°)");
    }

    private void applyFineAdjustment() {
        if (!reflectionMode.get()) {
            LogService.log("Подстройка доступна только в режиме Отражение");
            return;
        }

        int delta = (int) Math.round(sliderFineAngle.getValue());
        if (delta == 0) return;

        sendStepperCommand("MOVE 1 " + delta);
        sendStepperCommand("MOVE 2 " + delta);

        currentPosMotor1 += delta;
        currentPosMotor2 += delta;
        updatePositionDisplay();

        LogService.log("Подстройка на " + delta + "° применена");
    }

    // ────────────────────────────────────────────────────────────────
    // Вспомогательные методы
    // ────────────────────────────────────────────────
    private void sendCommand(String command) {
        LogService.log("CMD ▶ " + command);
        if (connectionService != null && connectionService.isConnected()) {
            connectionService.sendCommand(command);
        }
    }

    private void sendStepperCommand(String command) {
        if (!isArduinoConnected()) {
            LogService.log("Arduino не подключён");
            return;
        }

        try {
            OutputStream out = arduinoPort.getOutputStream();
            out.write((command + "\n").getBytes());
            out.flush();
            LogService.log("→ Arduino: " + command + " (текущая позиция: M1=" +
                    currentPosMotor1 + "°, M2=" + currentPosMotor2 + ")");
        } catch (IOException e) {
            LogService.error("Ошибка отправки команды шаговикам", e);
        }
    }

    private boolean isArduinoConnected() {
        return arduinoPort != null && arduinoPort.isOpen();
    }

    private void updateArduinoModeButton() {
        boolean canEnable = arduinoConnected.get();
        btnMode.setDisable(!canEnable);
        btnMode.setStyle(canEnable ? "" : "-fx-opacity: 0.6;");
    }

    private void handleLogKeyPress(KeyEvent event) {
        if (event.isControlDown() && event.getCode() == KeyCode.C) {
            copySelectedLogsToClipboard();
            event.consume();
        }
    }

    private void copySelectedLogsToClipboard() {
        ObservableList<String> selected = logView.getSelectionModel().getSelectedItems();
        if (!selected.isEmpty()) {
            String text = String.join("\n", selected);
            ClipboardContent content = new ClipboardContent();
            content.putString(text);
            Clipboard.getSystemClipboard().setContent(content);
        }
    }

    public VBox getView() {
        return view;
    }
}