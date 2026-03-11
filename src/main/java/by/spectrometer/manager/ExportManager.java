package by.spectrometer.manager;

import by.spectrometer.model.SpectrumData;
import by.spectrometer.service.ExportService;
import by.spectrometer.service.LogService;
import by.spectrometer.ui.SpectrumChart;
import javafx.scene.control.Alert;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.time.LocalDateTime;

public class ExportManager {

    private final SpectrumData data;
    private final SpectrumChart chart;

    public ExportManager(SpectrumData data, SpectrumChart chart) {
        this.data = data;
        this.chart = chart;
    }

    public void exportData(ExportService.ExportFormat format, Window ownerWindow) {
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
        File selectedFile = fileChooser.showSaveDialog(ownerWindow);

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

    private String currentDate() {
        LocalDateTime now = LocalDateTime.now();
        return now.getYear() + "-" + now.getMonth().getValue() + "-" + now.getDayOfMonth() + " " + now.getHour() + ":" + now.getMinute() + ":" + now.getSecond() + " ";
    }
}