package by.spectrometer.service;

import by.spectrometer.model.ConnectionState;
import by.spectrometer.model.SpectrumData;
import by.spectrometer.util.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import javafx.application.Platform;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;

public class WebSocketConnectionService extends ConnectionService {

    private WebSocketClient client;

    public WebSocketConnectionService(SpectrumData data, ConnectionState state, Runnable onNewSpectrum) {
        super(data, state, onNewSpectrum);
    }

    @Override
    public void connect(String url) {
        try {
            if (client != null && client.isOpen()) {
                client.close();
            }

            client = new WebSocketClient(new URI(url)) {
                @Override
                public void onOpen(ServerHandshake handshake) {
                    Platform.runLater(() -> {
                        state.setConnected("WebSocket: " + url);
                        state.setConnectionType("WebSocket");
                    });
                }

                @Override
                public void onMessage(String message) {
                    processMessage(message);
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    Platform.runLater(() -> state.setDisconnected(reason));
                }

                @Override
                public void onError(Exception ex) {
                    Platform.runLater(() -> state.setDisconnected(ex.getMessage()));
                }
            };

            state.setConnecting();
            client.connect();
        } catch (Exception e) {
            state.setDisconnected(e.getMessage());
        }
    }

    private void processMessage(String message) {
        try {
            JsonNode json = JsonUtils.MAPPER.readTree(message);
            updateSpectrumData(json);
            Platform.runLater(onNewSpectrum);
        } catch (Exception ignored) {
            // Handle parse error
        }
    }

    private void updateSpectrumData(JsonNode json) {
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

    @Override
    public void disconnect() {
        if (client != null) {
            client.close();
        }
    }

    @Override
    public boolean isConnected() {
        return client != null && client.isOpen();
    }

    @Override
    public void sendCommand(String command) {
        if (isConnected()) {
            client.send(command);
        }
    }
}