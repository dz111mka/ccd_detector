package by.spectrometer.service;

import by.spectrometer.model.ConnectionState;
import by.spectrometer.model.SimulationTemplate;
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
    private volatile SimulationTemplate simulationTemplate = SimulationTemplate.CHLOROPHYLL_VISIBLE;
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
        LogService.log("Simulator started: " + simulationTemplate);
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
        double lampShape = sourceShape(wavelength);
        double intensity = 3100.0 * integrationScale * clamp(lampShape, 0.15, 1.0);
        return clampAdc(dark - intensity);
    }

    private double sampleTransmission(double wavelength) {
        double absorbance = sampleAbsorbance(wavelength);
        return clamp(Math.pow(10.0, -absorbance), 0.02, 0.96);
    }

    private double sourceShape(double wavelength) {
        return switch (simulationTemplate) {
            case DEUTERIUM_UV_CLEAR_QUARTZ -> uvLamp(wavelength);
            case MERCURY_ARGON_CALIBRATION -> mercuryArgonLamp(wavelength);
            case TUNGSTEN_VISIBLE_NEUTRAL, WHITE_LED_BLUE_BLOCKER, CHLOROPHYLL_VISIBLE -> visibleLamp(wavelength);
            case HALOGEN_IR_HEAT_MIRROR, PHASE_PLATE_BROADBAND -> halogenIrLamp(wavelength);
            case NIR_LED_BANDPASS_940 -> nirLed(wavelength);
        };
    }

    private double sampleAbsorbance(double wavelength) {
        return switch (simulationTemplate) {
            case DEUTERIUM_UV_CLEAR_QUARTZ -> 0.02 + 0.45 * gaussian(wavelength, 260, 38);
            case MERCURY_ARGON_CALIBRATION -> 0.01;
            case TUNGSTEN_VISIBLE_NEUTRAL -> 0.32;
            case WHITE_LED_BLUE_BLOCKER -> 0.05 + 1.35 * gaussian(wavelength, 440, 70);
            case HALOGEN_IR_HEAT_MIRROR -> 0.04 + 1.15 * sigmoid(wavelength, 780, 35);
            case NIR_LED_BANDPASS_940 -> 0.02 + 1.8 * (1.0 - gaussian(wavelength, 940, 36));
            case CHLOROPHYLL_VISIBLE -> 0.08
                    + 0.95 * gaussian(wavelength, 430, 28)
                    + 0.75 * gaussian(wavelength, 662, 34)
                    + 0.20 * gaussian(wavelength, 485, 80);
            case PHASE_PLATE_BROADBAND -> 0.06 + 0.08 * Math.pow(Math.sin(wavelength / 42.0), 2);
        };
    }

    private double uvLamp(double wavelength) {
        return 0.18
                + 0.95 * gaussian(wavelength, 260, 85)
                + 0.55 * gaussian(wavelength, 365, 32)
                + 0.12 * gaussian(wavelength, 486, 25);
    }

    private double visibleLamp(double wavelength) {
        return 0.68
                + 0.18 * gaussian(wavelength, 530, 180)
                + 0.10 * gaussian(wavelength, 900, 260)
                - 0.08 * gaussian(wavelength, 430, 45);
    }

    private double halogenIrLamp(double wavelength) {
        double warmRise = 0.28 + 0.72 * sigmoid(wavelength, 720, 190);
        return warmRise + 0.18 * gaussian(wavelength, 1150, 360);
    }

    private double nirLed(double wavelength) {
        return 0.08 + 1.15 * gaussian(wavelength, 940, 55);
    }

    private double mercuryArgonLamp(double wavelength) {
        return 0.04
                + 0.75 * gaussian(wavelength, 254, 4)
                + 0.55 * gaussian(wavelength, 365, 5)
                + 0.70 * gaussian(wavelength, 436, 5)
                + 0.95 * gaussian(wavelength, 546, 6)
                + 0.60 * gaussian(wavelength, 577, 4)
                + 0.45 * gaussian(wavelength, 696, 5)
                + 0.38 * gaussian(wavelength, 763, 5)
                + 0.30 * gaussian(wavelength, 811, 6);
    }

    private double gaussian(double x, double center, double sigma) {
        double z = (x - center) / sigma;
        return Math.exp(-0.5 * z * z);
    }

    private double sigmoid(double x, double center, double slope) {
        return 1.0 / (1.0 + Math.exp(-(x - center) / slope));
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
        } else if (normalized.startsWith("SIM_TEMPLATE_")) {
            String templateName = normalized.substring("SIM_TEMPLATE_".length());
            try {
                simulationTemplate = SimulationTemplate.valueOf(templateName);
                LogService.log("Simulator template: " + simulationTemplate);
            } catch (IllegalArgumentException e) {
                LogService.log("Unknown simulator template: " + templateName);
            }
        }
    }
}
