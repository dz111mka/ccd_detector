package by.spectrometer.ui.builder;

import by.spectrometer.controller.ArduinoConnectionController;
import by.spectrometer.controller.SpectrometerController;
import by.spectrometer.controller.StepperMotorController;
import by.spectrometer.model.ConnectionType;
import by.spectrometer.model.SimulationTemplate;
import by.spectrometer.service.ExportService;
import by.spectrometer.ui.SpectrumChart;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

public class SpectrometerUIBuilder {

    private final SpectrometerController controller;

    public SpectrometerUIBuilder(SpectrometerController controller) {
        this.controller = controller;
    }

    public MenuBar createMenuBar() {
        MenuBar menuBar = new MenuBar();

        // File menu
        Menu fileMenu = new Menu("Файл");
        Menu exportMenu = new Menu("Экспорт");

        MenuItem exportCSV = new MenuItem("CSV");
        exportCSV.setOnAction(e -> controller.exportData(ExportService.ExportFormat.CSV));

        MenuItem exportExcel = new MenuItem("Excel (XLSX)");
        exportExcel.setOnAction(e -> controller.exportData(ExportService.ExportFormat.EXCEL));

        MenuItem exportPDF = new MenuItem("PDF");
        exportPDF.setOnAction(e -> controller.exportData(ExportService.ExportFormat.PDF));

        exportMenu.getItems().addAll(exportCSV, exportExcel, exportPDF);

        MenuItem exitItem = new MenuItem("Выход");
        exitItem.setOnAction(e -> System.exit(0));

        fileMenu.getItems().addAll(exportMenu, new SeparatorMenuItem(), exitItem);

        // View menu
        Menu viewMenu = new Menu("Вид");

        CheckMenuItem showGrid = new CheckMenuItem("Показать сетку");
        showGrid.setSelected(true);
        showGrid.setOnAction(e -> controller.getChart().setGridVisible(showGrid.isSelected()));

        CheckMenuItem showLegend = new CheckMenuItem("Показать легенду");
        showLegend.setSelected(true);
        showLegend.setOnAction(e -> controller.getChart().setLegendVisible(showLegend.isSelected()));

        CheckMenuItem darkTheme = new CheckMenuItem("Тёмная тема");
        darkTheme.setOnAction(e -> controller.toggleTheme());

        viewMenu.getItems().addAll(showGrid, showLegend, darkTheme);

        // Help menu
        Menu helpMenu = new Menu("Справка");
        MenuItem aboutItem = new MenuItem("О программе");
        aboutItem.setOnAction(e -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("О программе");
            alert.setHeaderText("DIY Спектрофотометр TCD1304");
            alert.setContentText("Версия 0.0.1\n\nПрограмма для управления спектрофотометром на базе TCD1304.\n" +
                    "Поддерживает измерение и анализ спектральных данных в диапазоне 190–2050 нм.");
            alert.showAndWait();
        });
        helpMenu.getItems().add(aboutItem);

        menuBar.getMenus().addAll(fileMenu, viewMenu, helpMenu);
        return menuBar;
    }

    public HBox buildConnectionPanel(ComboBox<ConnectionType> cbConnectionType, TextField tfAddress,
                                     ComboBox<String> cbSerialPorts, Button btnConnect, Label lblStatus) {
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

    /**
     * Builds the measurement-control HBox (buttons + peak threshold/width fields).
     * The exposure/capture-mode row is built separately via {@link #buildExposureRow}.
     */
    public HBox buildMeasurementControls(Button btnDark, Button btnRef, Button btnCapture,
                                         Button btnSmooth, Button btnAnalysis,
                                         Button btnZoom, Button btnZoomBack, Button btnZoomForward,
                                         Button btnThemeToggle, TextField tfPeakThreshold,
                                         TextField tfPeakWindow, Button btnTransmissionMode,
                                         Button btnClearBuffers) {
        HBox controls = new HBox(8);
        controls.setAlignment(Pos.CENTER_LEFT);
        controls.getChildren().addAll(
                btnDark, btnRef, btnCapture, btnSmooth, btnAnalysis,
                btnZoom, btnZoomBack, btnZoomForward, btnThemeToggle,
                btnTransmissionMode, btnClearBuffers,
                new Label("Порог:"), tfPeakThreshold,
                new Label("Окно:"), tfPeakWindow
        );
        tfPeakThreshold.setPrefWidth(70);
        tfPeakWindow.setPrefWidth(70);

        btnThemeToggle.setStyle("-fx-background-radius: 50%; -fx-min-width: 30; -fx-min-height: 30; " +
                "-fx-max-width: 30; -fx-max-height: 30;");

        return controls;
    }

    /**
     * Builds the second row: Integration-time dropdown + Capture-mode dropdown.
     *
     * @return VBox containing the labeled HBox row (can be embedded directly in parent layout)
     */
    public VBox buildExposureRow(ComboBox<String> cbIntegrationTime, ComboBox<String> cbCaptureMode,
                                 ComboBox<SimulationTemplate> cbSimulationTemplate,
                                 TextField tfCalibrationPixel,
                                 ComboBox<Double> cbCalibrationWavelength,
                                 Button btnAddCalibrationPoint,
                                 Button btnApplyCalibration,
                                 Button btnClearCalibration) {
        cbIntegrationTime.setPrefWidth(120);
        cbCaptureMode.setPrefWidth(150);
        cbSimulationTemplate.setPrefWidth(260);
        tfCalibrationPixel.setPrefWidth(90);
        cbCalibrationWavelength.setPrefWidth(130);

        HBox exposureRow = new HBox(12);
        exposureRow.setAlignment(Pos.CENTER_LEFT);
        exposureRow.setPadding(new Insets(4, 0, 0, 0));
        exposureRow.getChildren().addAll(
                new Label("Интеграция:"), cbIntegrationTime,
                new Label("Режим сбора:"), cbCaptureMode,
                new Label("Симуляция:"), cbSimulationTemplate
        );

        HBox calibrationRow = new HBox(8);
        calibrationRow.setAlignment(Pos.CENTER_LEFT);
        calibrationRow.getChildren().addAll(
                new Label("Градуировка: pixel"), tfCalibrationPixel,
                new Label("→ nm"), cbCalibrationWavelength,
                btnAddCalibrationPoint,
                btnApplyCalibration,
                btnClearCalibration
        );

        VBox rows = new VBox(6, exposureRow, calibrationRow);
        return rows;
    }

    /**
     * Factory: integration-time ComboBox matching C# comboBox2.
     * "10 µs" → INT_1, "20 µs" → INT_2, … "7.5 ms" → INT_10.
     */
    public ComboBox<String> createIntegrationTimeComboBox() {
        ComboBox<String> cb = new ComboBox<>();
        cb.getItems().addAll(
                "10 µs",
                "20 µs",
                "50 µs",
                "60 µs",
                "75 µs",
                "100 µs",
                "500 µs",
                "1.25 ms",
                "2.5 ms",
                "7.5 ms"
        );
        cb.setValue("100 µs");
        return cb;
    }

    /**
     * Factory: capture-mode ComboBox matching C# comboBox3.
     * Index 0 → 12-bit ADC, Index 1 → 8-bit ADC, Index 2 → packetised frame (0xFE header).
     */
    public ComboBox<String> createCaptureModeComboBox() {
        ComboBox<String> cb = new ComboBox<>();
        cb.getItems().addAll(
                "Все данные — 12 бит",
                "Все данные — 8 бит",
                "Пакетный режим (0xFE заголовок)"
        );
        cb.setValue("Все данные — 12 бит");
        return cb;
    }

    public ComboBox<SimulationTemplate> createSimulationTemplateComboBox() {
        ComboBox<SimulationTemplate> cb = new ComboBox<>();
        cb.getItems().addAll(SimulationTemplate.values());
        cb.setValue(SimulationTemplate.CHLOROPHYLL_VISIBLE);
        return cb;
    }

    public ComboBox<Double> createCalibrationWavelengthComboBox() {
        ComboBox<Double> cb = new ComboBox<>();
        cb.setEditable(true);
        cb.setPromptText("nm");
        cb.setConverter(new StringConverter<>() {
            @Override
            public String toString(Double value) {
                return value == null ? "" : String.format("%.2f", value);
            }

            @Override
            public Double fromString(String value) {
                if (value == null || value.isBlank()) {
                    return null;
                }
                return Double.parseDouble(value.trim().replace(',', '.'));
            }
        });
        return cb;
    }

    public VBox buildMainLayout(MenuBar menuBar, HBox connectionPanel, HBox measurementControls,
                                VBox exposureControls,
                                SpectrumChart chart, ListView<String> logView,
                                ArduinoConnectionController arduinoController,
                                StepperMotorController stepperController) {
        VBox mainContent = new VBox(15);
        mainContent.setPadding(new Insets(20));
        mainContent.getChildren().addAll(
                connectionPanel,
                measurementControls,
                exposureControls,
                chart,
                new Label("Логи:"),
                logView,
                arduinoController.getView(),
                stepperController.getView()
        );

        VBox view = new VBox();
        view.getChildren().addAll(
                menuBar,
                mainContent
        );
        return view;
    }
}
