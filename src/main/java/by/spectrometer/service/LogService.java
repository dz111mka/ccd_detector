package by.spectrometer.service;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.time.LocalTime;

public class LogService {

    private static final ObservableList<String> logs =
            FXCollections.observableArrayList();

    public static ObservableList<String> getLogs() {
        return logs;
    }

    public static void log(String msg) {
        String line = LocalTime.now()
                .withNano(0) + "  " + msg;

        System.out.println(line); // консоль
        Platform.runLater(() -> logs.add(line));
    }

    public static void error(String msg, Throwable e) {
        log("❌ " + msg + " : " + e.getMessage());
    }
}
