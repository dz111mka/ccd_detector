package by.spectrometer.service;

import by.spectrometer.model.ConnectionState;
import by.spectrometer.model.SpectrumData;
import javafx.application.Platform;

import java.util.Arrays;
import java.util.Random;

import static by.spectrometer.util.Constants.TRANSMISSION_BUFFER_FRAMES;

public class SimulatorConnectionService extends ConnectionService {

    private static final int DATA_POINTS = 3648;
    private static final long FRAME_DELAY_MS = 120;
    private static final double START_NM = 189.5;
    private static final double NM_PER_PIXEL = 0.48;

    private final Random random = new Random(42);

    private Thread simulationThread;
    private volatile boolean running = false;
    private double framePhase = 0.0;
    private double integrationScale = 1.0;

    public SimulatorConnectionService(SpectrumData data, ConnectionState state, Runnable onNewSpectrum) {
        super(data, state, onNewSpectrum);
    }

    @Override
    public void connect(String address) {
        disconnect();
        running = true;
        Arrays.fill(data.darkBuffer, 0.0);
        Arrays.fill(data.referenceBuffer, 0.0);
        generateWavelengths();

        simulationThread = new Thread(this::runSimulation, "spectrometer-simulator-thread");
        simulationThread.setDaemon(true);
        simulationThread.start();

        Platform.runLater(() -> {
            state.setConnected("Simulator");
            state.setConnectionType("Simulator");
        });
        LogService.log("Simulator started: chlorophyll-like sample with noise");
    }

    private void runSimulation() {
        while (running) {
            generateFrame();
            handleBufferAccumulation();
            Platform.runLater(onNewSpectrum);

            try {
                Thread.sleep(FRAME_DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void generateFrame() {
        framePhase += 0.035;

        for (int i = 0; i < DATA_POINTS; i++) {
            double wavelength = data.wavelength[i];
            double dark = darkSignal(i);
            double reference = referenceSignal(wavelength, dark);
            double transmission = sampleTransmission(wavelength);
            double raw = switch (data.recordingMode) {
                case DARK -> dark;
                case REFERENCE -> reference;
                case NONE -> dark - (dark - reference) * transmission;
            };

            double slowDrift = 8.0 * Math.sin(framePhase + i / 900.0);
            double noise = random.nextGaussian() * 5.0;
            data.raw[i] = clampAdc(raw + slowDrift + noise);
        }
    }

    private double darkSignal(int pixel) {
        return clampAdc(4070.0 - 10.0 * Math.sin(pixel / 700.0) + random.nextGaussian() * 2.0);
    }

    private double referenceSignal(double wavelength, double dark) {
        double lampShape = 0.68
                + 0.18 * gaussian(wavelength, 530, 180)
                + 0.10 * gaussian(wavelength, 900, 260)
                - 0.08 * gaussian(wavelength, 430, 45);
        double intensity = 3100.0 * integrationScale * clamp(lampShape, 0.15, 1.0);
        return clampAdc(dark - intensity);
    }

    private double sampleTransmission(double wavelength) {
        double absorbance = 0.08
                + 0.95 * gaussian(wavelength, 430, 28)
                + 0.75 * gaussian(wavelength, 662, 34)
                + 0.20 * gaussian(wavelength, 485, 80);
        return clamp(Math.pow(10.0, -absorbance), 0.02, 0.96);
    }

    private double gaussian(double x, double center, double sigma) {
        double z = (x - center) / sigma;
        return Math.exp(-0.5 * z * z);
    }

    private double clampAdc(double value) {
        return clamp(value, 0.0, 4095.0);
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private void handleBufferAccumulation() {
        if (data.recordingMode == SpectrumData.BufferType.NONE) {
            return;
        }

        if (data.bufferAccumulatorCount < TRANSMISSION_BUFFER_FRAMES) {
            data.accumulateFrame(data.raw, data.recordingMode);
            LogService.log(data.recordingMode + ": frame " + data.bufferAccumulatorCount + "/" + TRANSMISSION_BUFFER_FRAMES);
        }

        if (data.bufferAccumulatorCount >= TRANSMISSION_BUFFER_FRAMES) {
            if (data.recordingMode == SpectrumData.BufferType.DARK) {
                data.finalizeDarkBuffer(TRANSMISSION_BUFFER_FRAMES);
                LogService.log("Simulator dark signal recorded");
            } else if (data.recordingMode == SpectrumData.BufferType.REFERENCE) {
                data.finalizeReferenceBuffer(TRANSMISSION_BUFFER_FRAMES);
                LogService.log("Simulator reference signal recorded");
            }
            data.recordingMode = SpectrumData.BufferType.NONE;
        }
    }

    private void generateWavelengths() {
        for (int i = 0; i < DATA_POINTS; i++) {
            data.wavelength[i] = START_NM + NM_PER_PIXEL * i;
        }
    }

    @Override
    public void disconnect() {
        running = false;
        if (simulationThread != null) {
            simulationThread.interrupt();
            simulationThread = null;
        }
        Platform.runLater(() -> state.setDisconnected("Simulator stopped"));
    }

    @Override
    public boolean isConnected() {
        return running;
    }

    @Override
    public void sendCommand(String command) {
        String normalized = command.toUpperCase();
        if (normalized.startsWith("INT_")) {
            integrationScale = switch (normalized) {
                case "INT_1" -> 0.2;
                case "INT_2" -> 0.35;
                case "INT_3" -> 0.55;
                case "INT_4" -> 0.65;
                case "INT_5" -> 0.75;
                case "INT_6" -> 1.0;
                case "INT_7" -> 1.25;
                case "INT_8" -> 1.45;
                case "INT_9" -> 1.6;
                case "INT_10" -> 1.8;
                default -> integrationScale;
            };
            LogService.log("Simulator integration scale: " + String.format("%.2f", integrationScale));
        }
    }
}
