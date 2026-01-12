package by.spectrometer.service;

import by.spectrometer.model.ConnectionState;
import by.spectrometer.model.SpectrumData;
import by.spectrometer.util.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import javafx.application.Platform;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;

public class Esp32WebSocketService {

    private final SpectrumData data;
    private final ConnectionState state;
    private WebSocketClient client;

    private final Runnable onNewSpectrum;

    public Esp32WebSocketService(SpectrumData data, ConnectionState state, Runnable onNewSpectrum) {
        this.data = data;
        this.state = state;
        this.onNewSpectrum = onNewSpectrum;
    }

    public void connect(String url) {
        try {
            if (client != null && client.isOpen()) client.close();

            client = new WebSocketClient(new URI(url)) {
                @Override
                public void onOpen(ServerHandshake handshake) {
                    Platform.runLater(() -> state.setConnected(url));
                }

                @Override
                public void onMessage(String message) {
                    try {
                        JsonNode json = JsonUtils.MAPPER.readTree(message);

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
                            Platform.runLater(onNewSpectrum);
                        }
                    } catch (Exception ignored) {}
                }

                @Override public void onClose(int code, String reason, boolean remote) {
                    Platform.runLater(() -> state.setDisconnected(reason));
                }

                @Override public void onError(Exception ex) {
                    Platform.runLater(() -> state.setDisconnected(ex.getMessage()));
                }
            };
            state.setConnecting();
            client.connect();
        } catch (Exception e) {
            state.setDisconnected(e.getMessage());
        }
    }

    public void send(String json) {
        if (client != null && client.isOpen()) {
            client.send(json);
        }
    }

    public void disconnect() {
        if (client != null) client.close();
    }

    public boolean isConnected() {
        return client != null && client.isOpen();
    }
}