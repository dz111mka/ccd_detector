package by.spectrometer.controller;

import by.spectrometer.service.LogService;
import by.spectrometer.util.Constants;
import com.fazecast.jSerialComm.SerialPort;
import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.io.OutputStream;

public class StepperMotorController {

    // ────────────────────────────────────────────────────────────────
    // Свойства
    // ────────────────────────────────────────────────────────────────
    private final BooleanProperty reflectionMode = new SimpleBooleanProperty(false);
    private final IntegerProperty motor1Position = new SimpleIntegerProperty(0);
    private final IntegerProperty motor2Position = new SimpleIntegerProperty(0);
    private final ObjectProperty<SerialPort> arduinoPort = new SimpleObjectProperty<>();

    // ────────────────────────────────────────────────────────────────
    // UI компоненты
    // ────────────────────────────────────────────────────────────────
    private final VBox motorView;
    private final Button btnMode = new Button("Перейти в режим ОТРАЖЕНИЯ");
    private final Label lblMotor1Pos = new Label("Мотор 1: 0°");
    private final Label lblMotor2Pos = new Label("Мотор 2: 0°");

    // Компоненты тонкой настройки
    private final VBox reflectionControls = new VBox(10);
    private final Slider sliderFineAngle = new Slider(Constants.FINE_ADJUSTMENT_MIN, Constants.FINE_ADJUSTMENT_MAX, 0);
    private final Label lblFineAngle = new Label("0°");
    private final Button btnApplyFine = new Button("Применить подстройку");

    // ────────────────────────────────────────────────────────────────
    // Конструктор
    // ────────────────────────────────────────────────────────────────
    public StepperMotorController() {
        motorView = new VBox(10);
        initializeUI();
        setupBindings();
        setupEventHandlers();
        buildLayout();
        setupReflectionControls();
    }

    // ────────────────────────────────────────────────────────────────
    // Инициализация UI
    // ────────────────────────────────────────────────────────────────
    private void initializeUI() {
        btnMode.setDisable(true);
        btnMode.setStyle("-fx-opacity: 0.6;");
        lblMotor1Pos.setStyle("-fx-font-weight: bold;");
        lblMotor2Pos.setStyle("-fx-font-weight: bold;");
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
        // Привязка слайдера тонкой настройки
        sliderFineAngle.valueProperty().addListener((obs, old, val) ->
                lblFineAngle.setText(String.format("%+.0f°", val.doubleValue()))
        );

        // Привязка позиций моторов к лейблам
        motor1Position.addListener((obs, old, val) ->
                Platform.runLater(() -> lblMotor1Pos.setText("Мотор 1: " + val + "°"))
        );

        motor2Position.addListener((obs, old, val) ->
                Platform.runLater(() -> lblMotor2Pos.setText("Мотор 2: " + val + "°"))
        );

        // Привязка состояния подключения к доступности кнопок
        arduinoPort.addListener((obs, oldPort, newPort) -> {
            boolean isConnected = newPort != null && newPort.isOpen();
            btnMode.setDisable(!isConnected);
            btnMode.setStyle(isConnected ? "" : "-fx-opacity: 0.6;");
        });

        // Обработка изменения режима
        reflectionMode.addListener((obs, wasReflection, isNowReflection) ->
                handleMeasurementModeChange(isNowReflection)
        );
    }

    // ────────────────────────────────────────────────────────────────
    // Обработчики событий
    // ────────────────────────────────────────────────────────────────
    private void setupEventHandlers() {
        btnMode.setOnAction(e -> toggleMeasurementMode());
        btnApplyFine.setOnAction(e -> applyFineAdjustment());
    }

    // ────────────────────────────────────────────────────────────────
    // Построение layout
    // ────────────────────────────────────────────────────────────────
    private void buildLayout() {
        motorView.setPadding(new Insets(10, 0, 0, 0));

        VBox positionDisplay = new VBox(5, lblMotor1Pos, lblMotor2Pos);
        positionDisplay.setPadding(new Insets(10, 0, 0, 0));

        motorView.getChildren().addAll(
                new Label("Управление шаговыми двигателями"),
                new HBox(10, btnMode),
                reflectionControls,
                positionDisplay
        );
    }

    // ────────────────────────────────────────────────────────────────
    // Публичные методы
    // ────────────────────────────────────────────────────────────────
    public VBox getView() {
        return motorView;
    }

    public BooleanProperty reflectionModeProperty() {
        return reflectionMode;
    }

    public IntegerProperty motor1PositionProperty() {
        return motor1Position;
    }

    public IntegerProperty motor2PositionProperty() {
        return motor2Position;
    }

    public void setArduinoPort(SerialPort port) {
        this.arduinoPort.set(port);
    }

    // ────────────────────────────────────────────────────────────────
    // Основная логика управления двигателями
    // ────────────────────────────────────────────────────────────────
    private void toggleMeasurementMode() {
        boolean newMode = !reflectionMode.get();
        reflectionMode.set(newMode);
        LogService.log("Переключение в режим " + (newMode ? "ОТРАЖЕНИЕ" : "ПРОПУСКАНИЕ"));
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

    public void moveToZeroPosition() {
        sendStepperCommand("HOME");
        motor1Position.set(0);
        motor2Position.set(0);
        LogService.log("Двигатели перемещены в нулевую позицию (0°, 0°)");
    }

    private void moveToTransmissionPosition() {
        sendStepperCommand("HOME");
        motor1Position.set(Constants.TRANSMISSION_POS_MOTOR1);
        motor2Position.set(Constants.TRANSMISSION_POS_MOTOR2);
        LogService.log("Выбран режим ПРОПУСКАНИЯ");
    }

    private void moveToReflectionPosition() {
        int delta1 = Constants.REFLECTION_BASE_POS_MOTOR1 - motor1Position.get();
        int delta2 = Constants.REFLECTION_BASE_POS_MOTOR2 - motor2Position.get();

        sendStepperCommand("MOVE 1 " + delta1);
        sendStepperCommand("MOVE 2 " + delta2);

        motor1Position.set(Constants.REFLECTION_BASE_POS_MOTOR1);
        motor2Position.set(Constants.REFLECTION_BASE_POS_MOTOR2);
        LogService.log("Выбран режим ОТРАЖЕНИЯ (90°, 135°)");
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

        motor1Position.set(motor1Position.get() + delta);
        motor2Position.set(motor2Position.get() + delta);

        LogService.log("Подстройка на " + delta + "° применена");
    }

    private void sendStepperCommand(String command) {
        SerialPort port = arduinoPort.get();
        if (port == null || !port.isOpen()) {
            LogService.log("Arduino не подключён");
            return;
        }

        try {
            OutputStream out = port.getOutputStream();
            out.write((command + "\n").getBytes());
            out.flush();
            LogService.log("→ Arduino: " + command +
                    " (M1=" + motor1Position.get() + "°, M2=" + motor2Position.get() + "°)");
        } catch (IOException e) {
            LogService.error("Ошибка отправки команды шаговикам", e);
        }
    }
}