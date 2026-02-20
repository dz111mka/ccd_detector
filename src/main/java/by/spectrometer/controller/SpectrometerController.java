package by.spectrometer.controller;

import by.spectrometer.model.ConnectionState;
import by.spectrometer.model.ConnectionType;
import by.spectrometer.model.SpectrumData;
import by.spectrometer.model.Peak;
import by.spectrometer.service.ConnectionService;
import by.spectrometer.service.ExportService;
import by.spectrometer.service.LogService;
import by.spectrometer.service.SerialConnectionService;
import by.spectrometer.service.WebSocketConnectionService;
import by.spectrometer.ui.SpectrumChart;
import by.spectrometer.util.Constants;
import javafx.beans.binding.Bindings;
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
import javafx.stage.FileChooser;

import java.io.File;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.Arrays;
import java.util.DoubleSummaryStatistics;
import java.util.List;
import java.util.prefs.Preferences;

public class SpectrometerController {

    // ────────────────────────────────────────────────────────────────
    // Модели данных
    // ────────────────────────────────────────────────────────────────
    private final SpectrumData data = new SpectrumData();
    private final ConnectionState connState = new ConnectionState();

    // ────────────────────────────────────────────────────────────────
    // UI компоненты
    // ────────────────────────────────────────────────────────────────
    private final VBox view = new VBox(15);
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
    private final TextField tfPeakThreshold = new TextField("1000");
    private final TextField tfPeakWindow = new TextField("50");

    // ────────────────────────────────────────────────────────────────
    // Контроллеры для Arduino
    // ────────────────────────────────────────────────────────────────
    private final ArduinoConnectionController arduinoConnectionController = new ArduinoConnectionController();
    private final StepperMotorController stepperMotorController = new StepperMotorController();

    // ────────────────────────────────────────────────────────────────
    // Внутреннее состояние
    // ────────────────────────────────────────────────────────────────
    private ConnectionService connectionService;
    private ConnectionType currentConnectionType = ConnectionType.SERIAL;
    private long lastRedraw = 0;

    // ────────────────────────────────────────────────────────────────
    // Кнопки масштаба
    // ────────────────────────────────────────────────────────────────
    private final Button btnZoom = new Button("🔍");
    private final Button btnZoomBack = new Button("↶");
    private final Button btnZoomForward = new Button("↷");


    // ────────────────────────────────────────────────────────────────
    // Конструктор
    // ────────────────────────────────────────────────────────────────
    public SpectrometerController() {
        chart = new SpectrumChart(data);
        menuBar = createMenuBar();
        initializeUI();
        setupBindings();
        setupEventHandlers();
        buildLayout();
        loadConfiguration();
        refreshPorts();
        setupArduinoControllers();
    }

    // ────────────────────────────────────────────────────────────────
    // Создание меню
    // ────────────────────────────────────────────────────────────────
    private MenuBar createMenuBar() {
        MenuBar menuBar = new MenuBar();

        // File menu
        Menu fileMenu = new Menu("Файл");
        
        // Export submenu
        Menu exportMenu = new Menu("Экспорт");
        
        MenuItem exportCSV = new MenuItem("CSV");
        exportCSV.setOnAction(e -> exportData(ExportService.ExportFormat.CSV));
        
        MenuItem exportExcel = new MenuItem("Excel (XLSX)");
        exportExcel.setOnAction(e -> exportData(ExportService.ExportFormat.EXCEL));
        
        MenuItem exportPDF = new MenuItem("PDF");
        exportPDF.setOnAction(e -> exportData(ExportService.ExportFormat.PDF));
        
        exportMenu.getItems().addAll(exportCSV, exportExcel, exportPDF);
        
        // Exit menu item
        MenuItem exitItem = new MenuItem("Выход");
        exitItem.setOnAction(e -> System.exit(0));
        
        fileMenu.getItems().addAll(exportMenu, new SeparatorMenuItem(), exitItem);

        // View menu
        Menu viewMenu = new Menu("Вид");
        
        CheckMenuItem showGrid = new CheckMenuItem("Показать сетку");
        showGrid.setSelected(true);
        showGrid.setOnAction(e -> {
            // TODO: Implement grid visibility toggle
        });
        
        CheckMenuItem showLegend = new CheckMenuItem("Показать легенду");
        showLegend.setSelected(true);
        showLegend.setOnAction(e -> {
            // TODO: Implement legend visibility toggle
        });
        
        viewMenu.getItems().addAll(showGrid, showLegend);

        // Help menu
        Menu helpMenu = new Menu("Справка");
        
        MenuItem aboutItem = new MenuItem("О программе");
        aboutItem.setOnAction(e -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("О программе");
            alert.setHeaderText("DIY Спектрофотометр TCD1304");
            alert.setContentText("Версия 0.0.1\n\nПрограмма для управления спектрофотометром на базе TCD1304.\nПоддерживает измерение и анализ спектральных данных в диапазоне 190–2050 нм.");
            alert.showAndWait();
        });
        
        helpMenu.getItems().add(aboutItem);

        menuBar.getMenus().addAll(fileMenu, viewMenu, helpMenu);
        return menuBar;
    }

    private void exportData(ExportService.ExportFormat format) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Экспорт данных");
        
        // Set extension filters
        switch (format) {
            case CSV:
                fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
                fileChooser.setInitialFileName(currentDate() + "spectrum_data.csv");
                break;
            case EXCEL:
                fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel Files", "*.xlsx"));
                fileChooser.setInitialFileName(currentDate() + "spectrum_data.xlsx");
                break;
            case PDF:
                fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));
                fileChooser.setInitialFileName(currentDate() + "spectrum_data.pdf");
                break;
        }
        
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("All Files", "*.*"));
        
        // Show save dialog
        File selectedFile = fileChooser.showSaveDialog(view.getScene().getWindow());
        
        if (selectedFile != null) {
            try {
                double[] capturedY = chart.getCapturedY();
                ExportService.exportData(data, capturedY, format, selectedFile);
                LogService.log("Данные успешно экспортированы в: " + selectedFile.getAbsolutePath());
            } catch (Exception ex) {
                LogService.log("Ошибка при экспорте: " + ex.getMessage());
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Ошибка экспорта");
                alert.setHeaderText("Не удалось экспортировать данные");
                alert.setContentText(ex.getMessage());
                alert.showAndWait();
            }
        }
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
    }

    private void initializeLogView() {
        logView.setItems(LogService.getLogs());
        logView.setPrefHeight(180);
        logView.setStyle("""
            -fx-font-family: Consolas;
            -fx-font-size: 12;
        """);
    }

    private void configureVisualStyles() {
        view.setPadding(new Insets(20));
        view.setStyle("-fx-background-color: #f4f4f4;");
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
    }

    private void setupConnectionEventHandlers() {
        cbConnectionType.setOnAction(e -> handleConnectionTypeChange());
        btnConnect.setOnAction(e -> toggleConnection());
    }

    private void setupMeasurementEventHandlers() {
        btnDark.setOnAction(e -> sendCommand("DARK"));
        btnRef.setOnAction(e -> sendCommand("REF"));
        btnCapture.setOnAction(e -> toggleCaptureMode());
        btnSmooth.setOnAction(e -> applySmoothing());
        btnMinima.setOnAction(e -> findMinima());
        btnPeaks.setOnAction(e -> detectPeaks());
        btnZoom.setOnAction(e ->
                chart.setZoomMode(!chart.isZoomMode()
                ));

        btnZoomBack.setOnAction(e -> chart.zoomBack());
        btnZoomForward.setOnAction(e -> chart.zoomForward());
    }

    private void setupLogEventHandlers() {
        logView.setOnKeyPressed(this::handleLogKeyPress);
    }

    // ────────────────────────────────────────────────────────────────
    // Построение layout
    // ────────────────────────────────────────────────────────────────
    private void buildLayout() {
        view.getChildren().addAll(
                menuBar,
                buildConnectionPanel(),
                buildMeasurementControls(),
                chart,
                new Label("Логи:"),
                logView,
                arduinoConnectionController.getView(),
                stepperMotorController.getView()
        );
    }

    private HBox buildConnectionPanel() {
        HBox panel = new HBox(10);
        panel.setAlignment(Pos.CENTER_LEFT);
        panel.getChildren().addAll(
                new Label("Тип:"), cbConnectionType,
                new Label("Адрес:"), tfAddress,
                cbSerialPorts,
                btnConnect, lblStatus
        );
        return panel;
    }

    private HBox buildMeasurementControls() {
        HBox controls = new HBox(20, btnDark, btnRef, btnCapture, btnSmooth, btnMinima, btnPeaks, btnZoom, btnZoomBack, btnZoomForward);
        controls.getChildren().addAll(
                new Label("Порог:"), tfPeakThreshold,
                new Label("Окно:"), tfPeakWindow
        );
        tfPeakThreshold.setPrefWidth(80);
        tfPeakWindow.setPrefWidth(80);
        return controls;
    }

    // ────────────────────────────────────────────────────────────────
    // Основная логика подключения к спектрометру
    // ────────────────────────────────────────────────────────────────
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
        cbSerialPorts.getItems().clear();
        Arrays.stream(SerialPort.getCommPorts())
                .forEach(port -> cbSerialPorts.getItems().add(
                        port.getSystemPortName() + " - " + port.getDescriptivePortName()
                ));

        if (!cbSerialPorts.getItems().isEmpty()) {
            cbSerialPorts.setValue(cbSerialPorts.getItems().getFirst());
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

    // ────────────────────────────────────────────────────────────────
    // Логика управления измерениями
    // ────────────────────────────────────────────────────────────────
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

    private void detectPeaks() {
        if (!validatePeakDetection()) return;
        
        try {
            double threshold = Double.parseDouble(tfPeakThreshold.getText());
            int window = Integer.parseInt(tfPeakWindow.getText());
            
            List<Peak> peaks = chart.detectPeaks(threshold, window);
            LogService.log("Найдено пиков: " + peaks.size());
            
            for (Peak peak : peaks) {
                LogService.log(String.format("Пик в пикселе %d: высота=%.2f, ширина=%.2f, площадь=%.2f",
                        peak.getPixel(), peak.getHeight(), peak.getWidth(), peak.getArea()));
            }
        } catch (NumberFormatException e) {
            LogService.log("Ошибка: порог и окно должны быть числами");
        }
    }

    private boolean validatePeakDetection() {
        if (!chart.isFrozen()) {
            LogService.log("Детекция пиков доступна только в режиме Capture.");
            return false;
        }
        
        if (chart.getCapturedY() == null) {
            LogService.log("Детекция пиков не возможна: нет захваченных данных.");
            return false;
        }
        
        return true;
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
    // ────────────────────────────────────────────────────────────────
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
    // Вспомогательные методы
    // ────────────────────────────────────────────────────────────────
    private void sendCommand(String command) {
        LogService.log("CMD ▶ " + command);
        if (connectionService != null && connectionService.isConnected()) {
            connectionService.sendCommand(command);
        }
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

    private String currentDate() {
        LocalDateTime now = LocalDateTime.now();
        return now.getYear() + "-" + now.getMonth().getValue() + "-" + now.getDayOfMonth() + " " + now.getHour() + ":" + now.getMinute() + ":" + now.getSecond() + " ";
    }
}