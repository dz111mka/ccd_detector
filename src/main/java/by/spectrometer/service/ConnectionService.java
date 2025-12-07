package by.spectrometer.service;

import by.spectrometer.model.ConnectionState;
import by.spectrometer.model.SpectrumData;

public abstract class ConnectionService {
    protected final SpectrumData data;
    protected final ConnectionState state;
    protected final Runnable onNewSpectrum;

    public ConnectionService(SpectrumData data, ConnectionState state, Runnable onNewSpectrum) {
        this.data = data;
        this.state = state;
        this.onNewSpectrum = onNewSpectrum;
    }

    public abstract void connect(String address);
    public abstract void disconnect();
    public abstract boolean isConnected();
    public abstract void sendCommand(String command);
}