package by.spectrometer.controller;

import by.spectrometer.manager.*;
import by.spectrometer.model.ConnectionState;
import by.spectrometer.model.ConnectionType;
import by.spectrometer.model.SpectrumData;
import by.spectrometer.service.ExportService;
import by.spectrometer.service.LogService;
import by.spectrometer.ui.SpectrumChart;
import by.spectrometer.ui.builder.SpectrometerUIBuilder;
import by.spectrometer.util.Constants;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.VBox;

public class SpectrometerController {

    // ────────────────────────────────────────────────────────────────
    // Модели данных
    // ────────────────────────────────────────────────────────────────
    private final SpectrumData data = new SpectrumData();
    private final ConnectionState connState = new ConnectionState();

    // ────────────────────────────────────────────────────────────────
    // UI компоненты
    // ────────────────────────────────────────────────────────────────
    private final VBox view;
    private final SpectrumChart chart;
    private final ListView<String> logView = new ListView<>();
    private final MenuBar menuBar;

    // Компоненты подключения спектрометра
    private final ComboBox<ConnectionType> cbConnectionType = new ComboBox<>();
    private final TextField tfAddress = new TextField();
    private final ComboBox<String> cbSerialPorts = new ComboBox<>();
    private final Button btnConnect = new Button();
    private final Label lblStatus = new Label();

    // Кнопки управления измерением
    private final Button btnDark = new Button("Тёмный ток");
    private final Button btnRef = new Button("Белая опора");
    private final Button btnCapture = new Button("Захватить");
    private final Button btnMinima = new Button("Минимумы");
    private final Button btnSmooth = new Button("Сгладить");
    private final Button btnPeaks = new Button("Пики");
    private final Button btnTransmissionMode = new Button("Transmission Mode");
    private final Button btnClearBuffers = new Button("Clear Buffers");
    private final TextField tfPeakThreshold = new TextField("1000");
    private final TextField tfPeakWindow = new TextField("50");

    // comboBox2 equivalent — Integration time (SH period)
    private ComboBox<String> cbIntegrationTime;
    // comboBox3 equivalent — Capture mode
    private ComboBox<String> cbCaptureMode;

    // ────────────────────────────────────────────────────────────────
    // Контроллеры для Arduino
    // ────────────────────────────────────────────────────────────────
    private final ArduinoConnectionController arduinoConnectionController = new ArduinoConnectionController();
    private final StepperMotorController stepperMotorController = new StepperMotorController();

    // ────────────────────────────────────────────────────────────────
    // Менеджеры (новые классы для разделения ответственности)
    // ────────────────────────────────────────────────────────────────
    private final SpectrometerUIBuilder uiBuilder;
    private final ConnectionManager connectionManager;
    private final MeasurementManager measurementManager;
    private final ThemeManager themeManager;
    private final ConfigurationManager configManager;
    private final ExportManager exportManager;

    // ────────────────────────────────────────────────────────────────
    // Внутреннее состояние
    // ────────────────────────────────────────────────────────────────
    private long lastRedraw = 0;

    // Кнопки масштаба
    private final Button btnZoom = new Button("🔍");
    private final Button btnZoomBack = new Button("↶");
    private final Button btnZoomForward = new Button("↷");

    // Кнопка переключения темы
    private final Button btnThemeToggle = new Button("🌙");


    // ────────────────────────────────────────────────────────────────
    // Конструктор
    // ────────────────────────────────────────────────────────────────
    public SpectrometerController() {
        chart = new SpectrumChart(data);
        uiBuilder = new SpectrometerUIBuilder(this);
        menuBar = uiBuilder.createMenuBar();
        connectionManager = new ConnectionManager(this, data, connState);
        measurementManager = new MeasurementManager(this, chart);
        configManager = new ConfigurationManager(getClass());
        exportManager = new ExportManager(data, chart);

        // Create comboboxes now that uiBuilder is available
        cbIntegrationTime = uiBuilder.createIntegrationTimeComboBox();
        cbCaptureMode = uiBuilder.createCaptureModeComboBox();

        view = uiBuilder.buildMainLayout(menuBar,
                uiBuilder.buildConnectionPanel(cbConnectionType, tfAddress, cbSerialPorts, btnConnect, lblStatus),
                uiBuilder.buildMeasurementControls(btnDark, btnRef, btnCapture, btnSmooth, btnMinima, btnPeaks,
                        btnZoom, btnZoomBack, btnZoomForward, btnThemeToggle, tfPeakThreshold, tfPeakWindow,
                        btnTransmissionMode, btnClearBuffers),
                uiBuilder.buildExposureRow(cbIntegrationTime, cbCaptureMode),
                chart, logView, arduinoConnectionController, stepperMotorController);
        themeManager = new ThemeManager(this, view, menuBar);
        initializeUI();
        setupBindings();
        setupEventHandlers();
        loadConfiguration();
        refreshPorts();
        setupArduinoControllers();
        loadThemeConfiguration();
    }

    // ────────────────────────────────────────────────────────────────
    // Публичные методы для доступа из других классов
    // ────────────────────────────────────────────────────────────────
    public SpectrumData getData() {
        return data;
    }

    public SpectrumChart getChart() {
        return chart;
    }

    public void exportData(ExportService.ExportFormat format) {
        exportManager.exportData(format, view.getScene().getWindow());
    }

    public void toggleTheme() {
        themeManager.toggleTheme();
        btnThemeToggle.setText(themeManager.isDarkTheme() ? "☀" : "☽");
    }

    // ────────────────────────────────────────────────────────────────
    // Инициализация UI
    // ────────────────────────────────────────────────────────────────
    private void initializeUI() {
        initializeConnectionUI();
        initializeMeasurementUI();
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
        chart.setMaxHeight(600);
        chart.setPrefHeight(600);
        chart.setPrefWidth(2000);
        chart.setMaxWidth(2000);
    }

    private void initializeLogView() {
        logView.setItems(LogService.getLogs());
        logView.setPrefHeight(200);
        logView.setMaxHeight(200);
        logView.setStyle("""
                    -fx-font-family: Consolas;
                    -fx-font-size: 12;
                """);
        logView.setPrefWidth(2000);
        logView.setMaxWidth(2000);
    }

    private void configureVisualStyles() {
        view.setPadding(new Insets(20));
        applyTheme();
    }

    private void applyTheme() {
        themeManager.applyTheme();
        btnThemeToggle.setText(themeManager.isDarkTheme() ? "☀" : "☽");
    }

    // ────────────────────────────────────────────────────────────────
    // Настройка связей между контроллерами Arduino
    // ────────────────────────────────────────────────────────────────
    private void setupArduinoControllers() {
        // Когда меняется порт в контроллере подключения, передаем его контроллеру двигателей
        arduinoConnectionController.arduinoPortProperty().addListener((obs, oldPort, newPort) -> {
            stepperMotorController.setArduinoPort(newPort);

            // При подключении перемещаем в нулевую позицию
            if (newPort != null && newPort.isOpen()) {
                stepperMotorController.moveToZeroPosition();
            }
        });
    }

    // ────────────────────────────────────────────────────────────────
    // Настройка привязок
    // ────────────────────────────────────────────────────────────────
    private void setupBindings() {
        bindConnectionStatus();
        bindLogAutoScroll();
    }

    private void bindConnectionStatus() {
        lblStatus.textProperty().bind(connState.statusProperty());
        btnConnect.textProperty().bind(
                connState.connectedProperty()
                        .map(connected -> connected ? "Отключиться" : "Подключиться")
        );
    }

    private void bindLogAutoScroll() {
        LogService.getLogs().addListener((ListChangeListener<String>) change ->
                logView.scrollTo(LogService.getLogs().size() - 1)
        );
    }

    // ────────────────────────────────────────────────────────────────
    // Обработчики событий
    // ────────────────────────────────────────────────────────────────
    private void setupEventHandlers() {
        setupConnectionEventHandlers();
        setupMeasurementEventHandlers();
        setupLogEventHandlers();
        setupThemeEventHandlers();
    }

    private void setupThemeEventHandlers() {
        btnThemeToggle.setOnAction(e -> toggleTheme());
    }

    private void setupConnectionEventHandlers() {
        cbConnectionType.setOnAction(e -> connectionManager.handleConnectionTypeChange(cbConnectionType, cbSerialPorts, tfAddress));
        btnConnect.setOnAction(e -> connectionManager.toggleConnection(cbSerialPorts, tfAddress, lblStatus));
    }

    private void setupMeasurementEventHandlers() {
        btnDark.setOnAction(e -> measurementManager.recordDarkSignal());
        btnRef.setOnAction(e -> measurementManager.recordReferenceSignal());
        btnCapture.setOnAction(e -> measurementManager.toggleCaptureMode(btnCapture));
        btnSmooth.setOnAction(e -> measurementManager.applySmoothing());
        btnMinima.setOnAction(e -> measurementManager.findMinima());
        btnPeaks.setOnAction(e -> measurementManager.detectPeaks(tfPeakThreshold, tfPeakWindow));
        btnTransmissionMode.setOnAction(e -> measurementManager.toggleTransmissionMode(btnTransmissionMode));
        btnClearBuffers.setOnAction(e -> measurementManager.clearBuffers());
        btnZoom.setOnAction(e ->
                chart.setZoomMode(!chart.isZoomMode()
                ));

        btnZoomBack.setOnAction(e -> chart.zoomBack());
        btnZoomForward.setOnAction(e -> chart.zoomForward());

        // comboBox2 equivalent — send INT_n command on selection change
        cbIntegrationTime.setOnAction(e -> sendIntegrationTime());
    }

    /**
     * Maps the currently selected index of the integration-time comboBox to the
     * corresponding INT_1 … INT_10 firmware command and sends it to the device.
     * <pre>
     *   index 0 → INT_1  (SH = 20)
     *   index 1 → INT_2  (SH = 40)
     *   index 2 → INT_3  (SH = 100)
     *   index 3 → INT_4  (SH = 120)
     *   index 4 → INT_5  (SH = 150)
     *   index 5 → INT_6  (SH = 200)
     *   index 6 → INT_7  (SH = 1000)
     *   index 7 → INT_8  (SH = 2500)
     *   index 8 → INT_9  (SH = 5000)
     *   index 9 → INT_10 (SH = 15000)
     * </pre>
     */
    private void sendIntegrationTime() {
        int sel = cbIntegrationTime.getSelectionModel().getSelectedIndex();
        if (sel < 0 || sel > 9) return;
        String cmd = "INT_" + (sel + 1);
        connectionManager.sendCommand(cmd);
        LogService.log("Интеграция → " + cbIntegrationTime.getValue() + "  (cmd: " + cmd + ")");
    }

    private void setupLogEventHandlers() {
        logView.setOnKeyPressed(this::handleLogKeyPress);
    }

    // ────────────────────────────────────────────────────────────────
    // Вспомогательные методы
    // ────────────────────────────────────────────────────────────────
    private void refreshPorts() {
        connectionManager.refreshPorts(cbSerialPorts);
    }

    public void updateChart() {
        if (chart.isFrozen() || !shouldRedraw()) return;

        lastRedraw = System.currentTimeMillis();
        chart.redraw(data);
    }

    private boolean shouldRedraw() {
        return System.currentTimeMillis() - lastRedraw >= Constants.REDRAW_INTERVAL_MS;
    }

    private void loadConfiguration() {
        configManager.loadConnectionConfiguration(cbConnectionType, tfAddress);
        connectionManager.handleConnectionTypeChange(cbConnectionType, cbSerialPorts, tfAddress);
    }

    private void loadThemeConfiguration() {
        themeManager.loadThemeConfiguration();
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

    private void setupBufferListeners() {
        data.darkBufferReady = false;
        data.referenceBufferReady = false;

        // Можно добавить property для отслеживания, но пока просто в MeasurementManager
    }
}
