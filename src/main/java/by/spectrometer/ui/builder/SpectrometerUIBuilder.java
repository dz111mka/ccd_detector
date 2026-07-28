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
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

public class SpectrometerUIBuilder {

    private final SpectrometerController controller;

    public SpectrometerUIBuilder(SpectrometerController controller) {
        this.controller = controller;
    }

    public MenuBar createMenuBar() {
        MenuBar menuBar = new MenuBar();

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

        Menu helpMenu = new Menu("Справка");
        MenuItem aboutItem = new MenuItem("О программе");
        aboutItem.setOnAction(e -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("О программе");
            alert.setHeaderText("DIY Спектрофотометр TCD1304");
            alert.setContentText("Версия 0.0.1\n\n" +
                    "Программа для управления спектрофотометром на базе TCD1304.\n" +
                    "Поддерживает измерение и анализ спектральных данных.");
            alert.showAndWait();
        });
        helpMenu.getItems().add(aboutItem);

        menuBar.getMenus().addAll(fileMenu, viewMenu, helpMenu);
        return menuBar;
    }

    public VBox buildConnectionPanel(ComboBox<ConnectionType> cbConnectionType, TextField tfAddress,
                                     ComboBox<String> cbSerialPorts, Button btnConnect, Label lblStatus) {
        cbConnectionType.setMaxWidth(Double.MAX_VALUE);
        tfAddress.setMaxWidth(Double.MAX_VALUE);
        cbSerialPorts.setMaxWidth(Double.MAX_VALUE);
        btnConnect.setMaxWidth(Double.MAX_VALUE);
        lblStatus.setWrapText(true);

        GridPane fields = createFieldGrid();
        fields.addRow(0, new Label("Тип"), cbConnectionType);
        fields.addRow(1, new Label("Адрес"), tfAddress);
        fields.addRow(2, new Label("Порт"), cbSerialPorts);

        return createSection("Подключение", fields, btnConnect, lblStatus);
    }

    public VBox buildMeasurementControls(Button btnDark, Button btnRef, Button btnCapture,
                                         Button btnSmooth, Button btnAnalysis,
                                         Button btnZoom, Button btnZoomBack, Button btnZoomForward,
                                         Button btnThemeToggle, TextField tfPeakThreshold,
                                         TextField tfPeakWindow, Button btnTransmissionMode,
                                         Button btnClearBuffers) {
        setFullWidth(btnCapture, btnTransmissionMode, btnDark, btnRef, btnSmooth, btnAnalysis, btnClearBuffers);

        HBox bufferButtons = new HBox(8, btnDark, btnRef);
        bufferButtons.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(btnDark, Priority.ALWAYS);
        HBox.setHgrow(btnRef, Priority.ALWAYS);

        GridPane analysisFields = createFieldGrid();
        tfPeakThreshold.setPrefWidth(70);
        tfPeakWindow.setPrefWidth(70);
        analysisFields.addRow(0, new Label("Порог"), tfPeakThreshold);
        analysisFields.addRow(1, new Label("Окно"), tfPeakWindow);

        HBox viewButtons = new HBox(8, btnZoom, btnZoomBack, btnZoomForward, btnThemeToggle);
        viewButtons.setAlignment(Pos.CENTER_LEFT);

        return new VBox(10,
                createSection("Измерение", btnCapture, btnTransmissionMode, bufferButtons, btnClearBuffers),
                createSection("Обработка", btnSmooth, btnAnalysis, analysisFields),
                createSection("Вид", viewButtons)
        );
    }

    public VBox buildExposureRow(ComboBox<String> cbIntegrationTime, ComboBox<String> cbCaptureMode,
                                 ComboBox<SimulationTemplate> cbSimulationTemplate,
                                 TextField tfCalibrationPixel,
                                 ComboBox<Double> cbCalibrationWavelength,
                                 Button btnAddCalibrationPoint,
                                 Button btnApplyCalibration,
                                 Button btnClearCalibration) {
        cbIntegrationTime.setMaxWidth(Double.MAX_VALUE);
        cbCaptureMode.setMaxWidth(Double.MAX_VALUE);
        cbSimulationTemplate.setMaxWidth(Double.MAX_VALUE);
        tfCalibrationPixel.setPrefWidth(90);
        cbCalibrationWavelength.setMaxWidth(Double.MAX_VALUE);
        setFullWidth(btnAddCalibrationPoint, btnApplyCalibration, btnClearCalibration);

        GridPane exposureFields = createFieldGrid();
        exposureFields.addRow(0, new Label("Интеграция"), cbIntegrationTime);
        exposureFields.addRow(1, new Label("Сбор"), cbCaptureMode);
        exposureFields.addRow(2, new Label("Симуляция"), cbSimulationTemplate);

        GridPane calibrationFields = createFieldGrid();
        calibrationFields.addRow(0, new Label("Pixel"), tfCalibrationPixel);
        calibrationFields.addRow(1, new Label("nm"), cbCalibrationWavelength);

        HBox calibrationButtons = new HBox(8, btnAddCalibrationPoint, btnApplyCalibration, btnClearCalibration);

        return new VBox(10,
                createSection("Экспозиция", exposureFields),
                createSection("Градуировка", calibrationFields, calibrationButtons)
        );
    }

    public ComboBox<String> createIntegrationTimeComboBox() {
        ComboBox<String> cb = new ComboBox<>();
        cb.getItems().addAll(
                "10 us",
                "20 us",
                "50 us",
                "60 us",
                "75 us",
                "100 us",
                "500 us",
                "1.25 ms",
                "2.5 ms",
                "7.5 ms"
        );
        cb.setValue("100 us");
        return cb;
    }

    public ComboBox<String> createCaptureModeComboBox() {
        ComboBox<String> cb = new ComboBox<>();
        cb.getItems().addAll(
                "Все данные - 12 бит",
                "Все данные - 8 бит",
                "Пакетный режим (0xFE)"
        );
        cb.setValue("Все данные - 12 бит");
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

    public VBox buildMainLayout(MenuBar menuBar, VBox connectionPanel, VBox measurementControls,
                                VBox exposureControls,
                                SpectrumChart chart, ListView<String> logView,
                                ArduinoConnectionController arduinoController,
                                StepperMotorController stepperController) {
        VBox sidebar = new VBox(12,
                connectionPanel,
                measurementControls,
                exposureControls,
                createSection("Arduino", arduinoController.getView()),
                createSection("Двигатель", stepperController.getView())
        );
        sidebar.setPrefWidth(340);
        sidebar.setMinWidth(300);
        sidebar.setMaxWidth(380);

        ScrollPane sidebarScroll = new ScrollPane(sidebar);
        sidebarScroll.setFitToWidth(true);
        sidebarScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sidebarScroll.setPadding(new Insets(0));

        Label logTitle = new Label("Журнал");
        VBox chartArea = new VBox(10, chart, logTitle, logView);
        chartArea.setPadding(new Insets(0));
        VBox.setVgrow(chart, Priority.ALWAYS);

        BorderPane workspace = new BorderPane();
        workspace.setPadding(new Insets(16));
        workspace.setLeft(sidebarScroll);
        workspace.setCenter(chartArea);
        BorderPane.setMargin(sidebarScroll, new Insets(0, 16, 0, 0));

        VBox view = new VBox(menuBar, workspace);
        VBox.setVgrow(workspace, Priority.ALWAYS);
        return view;
    }

    private VBox createSection(String title, Node... children) {
        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-weight: bold;");

        VBox section = new VBox(8);
        section.setPadding(new Insets(10));
        section.setStyle("-fx-border-color: rgba(128, 128, 128, 0.35); " +
                "-fx-border-width: 1px; " +
                "-fx-border-radius: 6px; " +
                "-fx-background-radius: 6px;");
        section.getChildren().add(titleLabel);
        section.getChildren().addAll(children);
        return section;
    }

    private GridPane createFieldGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        return grid;
    }

    private void setFullWidth(Button... buttons) {
        for (Button button : buttons) {
            button.setMaxWidth(Double.MAX_VALUE);
        }
    }
}
