package by.spectrometer.ui.builder;

import by.spectrometer.controller.ArduinoConnectionController;
import by.spectrometer.controller.SpectrometerController;
import by.spectrometer.controller.StepperMotorController;
import by.spectrometer.model.ConnectionType;
import by.spectrometer.service.ExportService;
import by.spectrometer.ui.SpectrumChart;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

public class SpectrometerUIBuilder {

    private final SpectrometerController controller;

    public SpectrometerUIBuilder(SpectrometerController controller) {
        this.controller = controller;
    }

    public MenuBar createMenuBar() {
        MenuBar menuBar = new MenuBar();

        // File menu
        Menu fileMenu = new Menu("Файл");

        // Export submenu
        Menu exportMenu = new Menu("Экспорт");

        MenuItem exportCSV = new MenuItem("CSV");
        exportCSV.setOnAction(e -> controller.exportData(ExportService.ExportFormat.CSV));

        MenuItem exportExcel = new MenuItem("Excel (XLSX)");
        exportExcel.setOnAction(e -> controller.exportData(ExportService.ExportFormat.EXCEL));

        MenuItem exportPDF = new MenuItem("PDF");
        exportPDF.setOnAction(e -> controller.exportData(ExportService.ExportFormat.PDF));

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
            alert.setContentText("Версия 0.0.1\n\nПрограмма для управления спектрофотометром на базе TCD1304.\nПоддерживает измерение и анализ спектральных данных в диапазоне 190–2050 нм.");
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

    public HBox buildMeasurementControls(Button btnDark, Button btnRef, Button btnCapture,
                                          Button btnSmooth, Button btnMinima, Button btnPeaks,
                                          Button btnZoom, Button btnZoomBack, Button btnZoomForward,
                                          Button btnThemeToggle, TextField tfPeakThreshold,
                                          TextField tfPeakWindow) {
        HBox controls = new HBox(20, btnDark, btnRef, btnCapture, btnSmooth, btnMinima, btnPeaks, btnZoom, btnZoomBack, btnZoomForward, btnThemeToggle);
        controls.getChildren().addAll(
                new Label("Порог:"), tfPeakThreshold,
                new Label("Окно:"), tfPeakWindow
        );
        tfPeakThreshold.setPrefWidth(80);
        tfPeakWindow.setPrefWidth(80);

        // Настройка кнопки переключения темы
        btnThemeToggle.setStyle("-fx-background-radius: 50%; -fx-min-width: 30; -fx-min-height: 30; -fx-max-width: 30; -fx-max-height: 30;");

        return controls;
    }

    public VBox buildMainLayout(MenuBar menuBar, HBox connectionPanel, HBox measurementControls,
                                 SpectrumChart chart, ListView<String> logView,
                                 ArduinoConnectionController arduinoController,
                                 StepperMotorController stepperController) {
        VBox mainContent = new VBox(15);
        mainContent.setPadding(new Insets(20));
        mainContent.getChildren().addAll(
                connectionPanel,
                measurementControls,
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