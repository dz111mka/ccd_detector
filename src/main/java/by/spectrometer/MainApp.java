package by.spectrometer;

import by.spectrometer.controller.SpectrometerController;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class MainApp extends Application {

    @Override
    public void start(Stage stage) {
        SpectrometerController controller = new SpectrometerController();
        Scene scene = new Scene(controller.getView());

        stage.setScene(scene);
        stage.setTitle("DIY Спектрофотометр TCD1304 • 190–2050 нм");
        stage.setMaximized(true); // Запуск приложения в максимальном размере окна
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}