package by.spectrometer.service;

import by.spectrometer.model.ConnectionState;
import by.spectrometer.model.SpectrumData;
import by.spectrometer.util.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fazecast.jSerialComm.SerialPort;
import javafx.application.Platform;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class SerialConnectionService extends ConnectionService {

    private SerialPort serialPort;
    private BufferedReader reader;
    private Thread readThread;
    private volatile boolean running = false;

    public SerialConnectionService(SpectrumData data, ConnectionState state, Runnable onNewSpectrum) {
        super(data, state, onNewSpectrum);
    }

    @Override
    public void connect(String portName) {
        try {
            disconnect(); // Close existing connection

            serialPort = SerialPort.getCommPort(portName);
            serialPort.setBaudRate(115200);
            serialPort.setNumDataBits(8);
            serialPort.setNumStopBits(1);
            serialPort.setParity(SerialPort.NO_PARITY);

            if (serialPort.openPort()) {
                reader = new BufferedReader(
                        new InputStreamReader(serialPort.getInputStream(), StandardCharsets.UTF_8));

                state.setConnecting();
                running = true;

                readThread = new Thread(this::readSerialData);
                readThread.setDaemon(true);
                readThread.start();

                Platform.runLater(() ->
                        state.setConnected("Serial: " + portName + " @ 115200 baud"));
            } else {
                Platform.runLater(() ->
                        state.setDisconnected("Не удалось открыть порт " + portName));
            }
        } catch (Exception e) {
            Platform.runLater(() -> state.setDisconnected(e.getMessage()));
        }
    }

    private void readSerialData() {
        try {
            String line;
            while (running && (line = reader.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    processSerialMessage(line);
                }
            }
        } catch (Exception e) {
            if (running) {
                Platform.runLater(() -> state.setDisconnected(e.getMessage()));
            }
        }
    }

    private void processSerialMessage(String message) {
        try {
            // Arduino может отправлять данные в разных форматах
            // Предполагаем JSON формат как у ESP32
            JsonNode json = JsonUtils.MAPPER.readTree(message);
            updateSpectrumData(json);
            Platform.runLater(onNewSpectrum);
        } catch (Exception e) {
            // Попробуем парсить как сырые данные
            try {
                parseRawData(message);
                Platform.runLater(onNewSpectrum);
            } catch (Exception ex) {
                System.err.println("Не удалось распарсить сообщение: " + message);
            }
        }
    }

    private void updateSpectrumData(JsonNode json) {
        // Та же логика, что и в WebSocket
        if (json.has("dark")) {
            JsonUtils.copy(json.get("dark"), data.dark);
            data.hasDark = true;
        }
        if (json.has("ref")) {
            JsonUtils.copy(json.get("ref"), data.reference);
            data.hasRef = true;
        }
        if (json.has("data") && json.has("wl")) {
            JsonUtils.copy(json.get("wl"), data.wavelength);
            JsonUtils.copy(json.get("data"), data.raw);
        }
    }

    private void parseRawData(String rawData) {
        // Для Arduino без JSON - парсим CSV или бинарный формат
        String[] parts = rawData.split(",");
        if (parts.length >= 256) {
            for (int i = 0; i < 256; i++) {
                try {
                    data.raw[i] = Double.parseDouble(parts[i]);
                    // Генерируем фиктивные длины волн
                    data.wavelength[i] = 190 + (2050 - 190) * i / 255.0;
                } catch (NumberFormatException e) {
                    data.raw[i] = 0;
                }
            }
        }
    }

    @Override
    public void disconnect() {
        running = false;

        if (readThread != null) {
            readThread.interrupt();
            readThread = null;
        }

        if (serialPort != null && serialPort.isOpen()) {
            serialPort.closePort();
        }

        Platform.runLater(() -> state.setDisconnected("Отключено"));
    }

    @Override
    public boolean isConnected() {
        return serialPort != null && serialPort.isOpen();
    }

    @Override
    public void sendCommand(String command) {
        if (isConnected()) {
            byte[] bytes = (command + "\n").getBytes(StandardCharsets.UTF_8);
            serialPort.writeBytes(bytes, bytes.length);
        }
    }
}