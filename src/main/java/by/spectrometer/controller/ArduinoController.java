package by.spectrometer.controller;

import by.spectrometer.service.LogService;
import by.spectrometer.util.Constants;
import com.fazecast.jSerialComm.SerialPort;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;

public class ArduinoController {

    // ────────────────────────────────────────────────────────────────
    // Свойства
    // ────────────────────────────────────────────────────────────────
    private final BooleanProperty arduinoConnected = new SimpleBooleanProperty(false);
    private final BooleanProperty reflectionMode = new SimpleBooleanProperty(false);

    // ────────────────────────────────────────────────────────────────
    // UI компоненты
    // ────────────────────────────────────────────────────────────────
    private final VBox arduinoView;

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
    // ────────────────────────────────────────────────────────────────
    private SerialPort arduinoPort;
    private int currentPosMotor1 = 0;
    private int currentPosMotor2 = 0;

    // ────────────────────────────────────────────────────────────────
    // Конструктор
    // ────────────────────────────────────────────────────────────────
    public ArduinoController() {
        arduinoView = new VBox(10);
        initializeUI();
        setupBindings();
        setupEventHandlers();
        buildLayout();
        setupReflectionControls();
        refreshArduinoPorts();
    }

    // ────────────────────────────────────────────────────────────────
    // Инициализация UI
    // ────────────────────────────────────────────────────────────────
    private void initializeUI() {
        cbArduinoPort.setPromptText("Выберите порт Arduino");
        btnMode.setDisable(true);
        lblMotor1Pos.setStyle("-fx-font-weight: bold;");
        lblMotor2Pos.setStyle("-fx-font-weight: bold;");

        if (!arduinoConnected.get()) {
            btnMode.setStyle("-fx-opacity: 0.6;");
        }
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

    // ────────────────────────────────────────────────────────────────
    // Настройка привязок
    // ────────────────────────────────────────────────────────────────
    private void setupBindings() {
        bindFineAdjustmentSlider();
        bindArduinoConnectionState();
    }

    private void bindFineAdjustmentSlider() {
        sliderFineAngle.valueProperty().addListener((obs, old, val) ->
                lblFineAngle.setText(String.format("%+.0f°", val.doubleValue()))
        );
    }

    private void bindArduinoConnectionState() {
        reflectionMode.addListener((obs, wasReflection, isNowReflection) ->
                handleMeasurementModeChange(isNowReflection)
        );
    }

    // ────────────────────────────────────────────────────────────────
    // Обработчики событий
    // ────────────────────────────────────────────────────────────────
    private void setupEventHandlers() {
        btnConnectArduino.setOnAction(e -> toggleArduinoConnection());
        btnApplyFine.setOnAction(e -> applyFineAdjustment());
        btnMode.setOnAction(e -> toggleMeasurementMode());
    }

    // ────────────────────────────────────────────────────────────────
    // Построение layout
    // ────────────────────────────────────────────────────────────────
    private void buildLayout() {
        arduinoView.setPadding(new Insets(10, 0, 0, 0));

        HBox connectionBox = new HBox(10, cbArduinoPort, btnConnectArduino, btnMode);
        connectionBox.setAlignment(Pos.CENTER_LEFT);

        VBox positionDisplay = new VBox(5, lblMotor1Pos, lblMotor2Pos);
        positionDisplay.setPadding(new Insets(10, 0, 0, 0));

        arduinoView.getChildren().addAll(
                new Label("Управление сервоприводом"),
                connectionBox,
                reflectionControls,
                positionDisplay
        );
    }

    // ────────────────────────────────────────────────────────────────
    // Публичные методы
    // ────────────────────────────────────────────────────────────────
    public VBox getView() {
        return arduinoView;
    }

    public BooleanProperty arduinoConnectedProperty() {
        return arduinoConnected;
    }

    public BooleanProperty reflectionModeProperty() {
        return reflectionMode;
    }

    public boolean isArduinoConnected() {
        return arduinoPort != null && arduinoPort.isOpen();
    }

    // ────────────────────────────────────────────────────────────────
    // Основная логика управления Arduino
    // ────────────────────────────────────────────────────────────────
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

    private void updateArduinoModeButton() {
        boolean canEnable = arduinoConnected.get();
        btnMode.setDisable(!canEnable);
        btnMode.setStyle(canEnable ? "" : "-fx-opacity: 0.6;");
    }
}