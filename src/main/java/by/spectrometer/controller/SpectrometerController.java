package by.spectrometer.controller;

import by.spectrometer.model.ConnectionState;
import by.spectrometer.model.SpectrumData;
import by.spectrometer.service.Esp32WebSocketService;
import by.spectrometer.ui.SpectrumChart;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.prefs.Preferences;

public class SpectrometerController {

    private final VBox view = new VBox(15);
    private final SpectrumData data = new SpectrumData();
    private final ConnectionState connState = new ConnectionState();
    private Esp32WebSocketService ws;

    private final TextField tfIp = new TextField();
    private final TextField tfPort = new TextField("81");
    private final Button btnConnect = new Button();
    private final Label lblStatus = new Label();
    private final CheckBox cbAbs = new CheckBox("Показывать Absorbance");
    private final SpectrumChart chart;

    public SpectrometerController() {
        ws = new Esp32WebSocketService(data, connState, this::updateChart);

        chart = new SpectrumChart(data);

        setupUI();
        loadLastConnection();
    }

    private void setupUI() {
        tfIp.setPrefWidth(140);
        tfPort.setPrefWidth(70);
        btnConnect.setOnAction(e -> toggleConnection());
        cbAbs.setOnAction(e -> {
            chart.setShowAbsorbance(cbAbs.isSelected());
            chart.redraw(data);
        });

        lblStatus.textProperty().bind(connState.statusProperty());
        btnConnect.textProperty().bind(
                connState.connectedProperty().map(c -> c ? "Отключиться" : "Подключиться")
        );

        HBox connBox = new HBox(10, new Label("WS://"), tfIp, new Label(":"), tfPort, btnConnect, lblStatus);
        connBox.setAlignment(Pos.CENTER_LEFT);

        Button btnDark = new Button("Тёмный ток");
        Button btnRef  = new Button("Белая опора");
        Button btnLive = new Button("Live ON");

        btnDark.setOnAction(e -> ws.send("{\"cmd\":\"dark\"}"));
        btnRef.setOnAction(e -> ws.send("{\"cmd\":\"ref\"}"));
        btnLive.setOnAction(e -> {
            boolean on = btnLive.getText().contains("ON");
            btnLive.setText(on ? "Live OFF" : "Live ON");
            ws.send(on ? "{\"cmd\":\"live\",\"on\":true}" : "{\"cmd\":\"live\",\"on\":false}");
        });

        HBox controls = new HBox(20, btnDark, btnRef, btnLive, cbAbs);

        view.setPadding(new Insets(20));
        view.getChildren().addAll(connBox, controls, chart);
        view.setStyle("-fx-background-color: #f4f4f4;");
    }

    private void toggleConnection() {
        if (connState.connectedProperty().get()) {
            ws.disconnect();
        } else {
            String url = "ws://" + tfIp.getText().trim() + ":" + tfPort.getText().trim();
            ws.connect(url);
            saveLastConnection();
        }
    }

    private void updateChart() {
        chart.redraw(data);
    }

    private void loadLastConnection() {
        Preferences p = Preferences.userNodeForPackage(getClass());
        tfIp.setText(p.get("ip", "192.168.1.77"));
        tfPort.setText(p.get("port", "81"));
    }

    private void saveLastConnection() {
        Preferences p = Preferences.userNodeForPackage(getClass());
        p.put("ip", tfIp.getText());
        p.put("port", tfPort.getText());
    }

    public VBox getView() { return view; }
}