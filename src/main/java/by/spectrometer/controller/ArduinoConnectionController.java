package by.spectrometer.controller;

import by.spectrometer.service.LogService;
import com.fazecast.jSerialComm.SerialPort;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.Arrays;

public class ArduinoConnectionController {

    // ────────────────────────────────────────────────────────────────
    // Свойства
    // ────────────────────────────────────────────────────────────────
    private final ObjectProperty<SerialPort> arduinoPort = new SimpleObjectProperty<>();

    // ────────────────────────────────────────────────────────────────
    // UI компоненты
    // ────────────────────────────────────────────────────────────────
    private final VBox connectionView;
    private final ComboBox<String> cbArduinoPort = new ComboBox<>();
    private final Button btnConnect = new Button("Подключить Arduino");
    private final Label lblStatus = new Label("Не подключено");

    // ────────────────────────────────────────────────────────────────
    // Конструктор
    // ────────────────────────────────────────────────────────────────
    public ArduinoConnectionController() {
        connectionView = new VBox(10);
        initializeUI();
        setupBindings();
        setupEventHandlers();
        buildLayout();
        refreshPorts();
    }

    // ────────────────────────────────────────────────────────────────
    // Инициализация UI
    // ────────────────────────────────────────────────────────────────
    private void initializeUI() {
        cbArduinoPort.setPromptText("Выберите порт Arduino");
        cbArduinoPort.setPrefWidth(200);
    }

    // ────────────────────────────────────────────────────────────────
    // Настройка привязок
    // ────────────────────────────────────────────────────────────────
    private void setupBindings() {
        // Привязка статуса к состоянию подключения
        arduinoPort.addListener((obs, oldPort, newPort) -> {
            if (newPort != null && newPort.isOpen()) {
                lblStatus.setText("Подключено");
                btnConnect.setText("Отключить Arduino");
            } else {
                lblStatus.setText("Не подключено");
                btnConnect.setText("Подключить Arduino");
            }
        });
    }

    // ────────────────────────────────────────────────────────────────
    // Обработчики событий
    // ────────────────────────────────────────────────────────────────
    private void setupEventHandlers() {
        btnConnect.setOnAction(e -> toggleConnection());
    }

    // ────────────────────────────────────────────────────────────────
    // Построение layout
    // ────────────────────────────────────────────────────────────────
    private void buildLayout() {
        connectionView.setPadding(new Insets(10, 0, 0, 0));

        HBox connectionBox = new HBox(10, cbArduinoPort, btnConnect, lblStatus);
        connectionBox.setAlignment(Pos.CENTER_LEFT);

        connectionView.getChildren().addAll(
                new Label("Подключение Arduino"),
                connectionBox
        );
    }

    // ────────────────────────────────────────────────────────────────
    // Публичные методы
    // ────────────────────────────────────────────────────────────────
    public VBox getView() {
        return connectionView;
    }

    public ObjectProperty<SerialPort> arduinoPortProperty() {
        return arduinoPort;
    }

    public SerialPort getArduinoPort() {
        return arduinoPort.get();
    }

    public boolean isConnected() {
        SerialPort port = arduinoPort.get();
        return port != null && port.isOpen();
    }

    // ────────────────────────────────────────────────────────────────
    // Основная логика подключения
    // ────────────────────────────────────────────────────────────────
    private void refreshPorts() {
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
        if (isConnected()) {
            disconnect();
        } else {
            connect();
        }
    }

    private void connect() {
        String selected = cbArduinoPort.getValue();
        if (selected == null || selected.isEmpty()) {
            LogService.log("Выберите порт Arduino");
            return;
        }

        String portName = selected.split(" - ")[0].trim();
        SerialPort port = SerialPort.getCommPort(portName);
        port.setBaudRate(115200);
        port.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 100, 100);

        if (port.openPort()) {
            arduinoPort.set(port);
            LogService.log("Arduino подключен на порту " + portName + " @ 115200");
        } else {
            LogService.log("Ошибка открытия порта " + portName);
            arduinoPort.set(null);
        }
    }

    private void disconnect() {
        SerialPort port = arduinoPort.get();
        if (port != null && port.isOpen()) {
            port.closePort();
            arduinoPort.set(null);
            LogService.log("Arduino отключен");
        }
    }
}