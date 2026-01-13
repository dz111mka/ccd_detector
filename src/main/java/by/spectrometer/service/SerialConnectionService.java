package by.spectrometer.service;

import by.spectrometer.model.ConnectionState;
import by.spectrometer.model.SpectrumData;
import com.fazecast.jSerialComm.SerialPort;
import javafx.application.Platform;

import java.io.*;

public class SerialConnectionService extends ConnectionService {

    private static final int DATA_POINTS      = 3648;
    private static final int FRAME_SIZE_12BIT = 7296;   // 3648 * 2 bytes
    private static final int RING_BUFFER_SIZE = 65536;  // 64 KB — enough for ~8+ frames

    private SerialPort port;
    private InputStream in;
    private OutputStream out;

    private Thread readerThread;
    private Thread processorThread;
    private volatile boolean running = false;

    private int adcMode = 0; // 0 = 12-bit, 1 = 8-bit

    // Ring buffer
    private final byte[] ringBuffer = new byte[RING_BUFFER_SIZE];
    private volatile int writePosition = 0;
    private volatile int readPosition  = 0;
    private volatile int bytesAvailable = 0;

    private final Object dataAvailableLock = new Object();


    public SerialConnectionService(
            SpectrumData data,
            ConnectionState state,
            Runnable onNewSpectrum) {
        super(data, state, onNewSpectrum);
    }

    // ────────────────────────────────────────────────────────────────
    // Connection
    // ────────────────────────────────────────────────────────────────
    @Override
    public void connect(String portName) {
        disconnect();

        try {
            port = SerialPort.getCommPort(portName);
            configurePort(port);

            if (!port.openPort()) {
                state.setDisconnected("Failed to open port");
                return;
            }

            in  = port.getInputStreamWithSuppressedTimeoutExceptions();
            out = port.getOutputStream();

            running = true;
            startReaderThread();
            startProcessorThread();

            Platform.runLater(() ->
                    state.setConnected("USB CCD: " + portName));

            // Start acquisition in 12-bit mode by default
            sendCommand("START_12BIT");

        } catch (Exception e) {
            LogService.error("Connection error to port " + portName, e);
            Platform.runLater(() -> state.setDisconnected(e.getMessage()));
        }
    }

    private void configurePort(SerialPort p) {
        p.setBaudRate(921600);
        p.setNumDataBits(8);
        p.setNumStopBits(1);
        p.setParity(SerialPort.NO_PARITY);
        p.setComPortTimeouts(
                SerialPort.TIMEOUT_READ_SEMI_BLOCKING,
                100,  // read timeout
                0     // write timeout
        );
        p.setDTR();
        p.setRTS();
    }

    // ────────────────────────────────────────────────────────────────
    // Reader thread
    // ────────────────────────────────────────────────────────────────
    private void startReaderThread() {
        readerThread = new Thread(this::readerLoop, "serial-reader");
        readerThread.setDaemon(true);
        readerThread.start();
    }

    private void readerLoop() {
        byte[] chunk = new byte[4096];

        try {
            while (running) {
                int bytesRead = in.read(chunk);
                if (bytesRead < 0) {
                    LogService.log("Stream ended (read = -1)");
                    running = false;
                    break;
                }

                if (bytesRead == 0) {
                    continue;
                }

                putBytesToRingBuffer(chunk, 0, bytesRead);

                // Log only larger chunks or when buffer is filling up
                if (bytesRead >= 1024 || bytesAvailable > 20000) {
                    LogService.log("Read " + bytesRead + " bytes, buffer: " + bytesAvailable);
                }
            }
        } catch (IOException e) {
            if (running) {
                LogService.error("Read error from port", e);
            }
        } finally {
            running = false;
        }
    }

    private void putBytesToRingBuffer(byte[] src, int offset, int length) {
        synchronized (dataAvailableLock) {
            for (int i = 0; i < length; i++) {
                if (bytesAvailable >= RING_BUFFER_SIZE) {
                    readPosition = (readPosition + 1) % RING_BUFFER_SIZE;
                } else {
                    bytesAvailable++;
                }

                ringBuffer[writePosition] = src[offset + i];
                writePosition = (writePosition + 1) % RING_BUFFER_SIZE;
            }

            dataAvailableLock.notifyAll();
        }
    }

    // ────────────────────────────────────────────────────────────────
    // Processor thread
    // ────────────────────────────────────────────────────────────────
    private void startProcessorThread() {
        processorThread = new Thread(this::processorLoop, "serial-processor");
        processorThread.setDaemon(true);
        processorThread.start();
    }

    private void processorLoop() {
        try {
            while (running) {
                synchronized (dataAvailableLock) {
                    while (bytesAvailable < FRAME_SIZE_12BIT && running) {
                        dataAvailableLock.wait(500);
                    }
                    if (!running) break;
                }

                processAvailableData();

                Thread.sleep(10);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            LogService.error("Processor thread error", e);
        }
    }

    private void processAvailableData() {
        synchronized (dataAvailableLock) {
            int available = bytesAvailable;

            if (adcMode == 0 && available >= FRAME_SIZE_12BIT) {
                LogService.log("Processing 12-bit frame, bytes: " + available);

                for (int i = 0; i < DATA_POINTS; i++) {
                    int lo = getByteFromRing() & 0xFF;
                    int hi = getByteFromRing() & 0xFF;
                    data.raw[i] = (hi << 8) | lo;
                }

                bytesAvailable -= FRAME_SIZE_12BIT;

                generateWavelengths();
                Platform.runLater(onNewSpectrum);

                sendCommand("START_12BIT");
                LogService.log("Next frame requested");
            }
            else if (available > RING_BUFFER_SIZE * 0.9) {
                LogService.log("Ring buffer almost full: " + available + " bytes");
                readPosition = writePosition;
                bytesAvailable = 0;
            }
        }
    }

    private byte getByteFromRing() {
        byte b = ringBuffer[readPosition];
        readPosition = (readPosition + 1) % RING_BUFFER_SIZE;
        bytesAvailable--;
        return b;
    }


    // ────────────────────────────────────────────────────────────────
    // Commands
    // ────────────────────────────────────────────────────────────────
    @Override
    public void sendCommand(String command) {
        byte cmd = switch (command.toUpperCase()) {
            case "START_12BIT" -> { adcMode = 0; yield (byte) 0xA1; }
            case "START_8BIT"  -> { adcMode = 1; yield (byte) 0xA2; }
            case "STATS"       -> (byte) 0xA3;
            case "INT_1"       -> (byte) 0xB1;
            case "INT_2"       -> (byte) 0xB2;
            case "INT_3"       -> (byte) 0xB3;
            case "INT_4"       -> (byte) 0xB4;
            case "INT_5"       -> (byte) 0xB5;
            case "INT_6"       -> (byte) 0xB6;
            case "INT_7"       -> (byte) 0xB7;
            case "INT_8"       -> (byte) 0xB8;
            case "INT_9"       -> (byte) 0xB9;
            case "INT_10"      -> (byte) 0xBA;
            default            -> 0;
        };

        if (cmd == 0) return;

        try {
            out.write(cmd);
            out.flush();

            synchronized (dataAvailableLock) {
                readPosition = writePosition;
                bytesAvailable = 0;
            }

            LogService.log("Command sent: " + command);

        } catch (IOException e) {
            LogService.error("Failed to send command: " + command, e);
        }
    }

    // ────────────────────────────────────────────────────────────────
    // Disconnect
    // ────────────────────────────────────────────────────────────────
    @Override
    public void disconnect() {
        running = false;

        try {
            if (readerThread != null)    readerThread.interrupt();
            if (processorThread != null) processorThread.interrupt();
            if (port != null && port.isOpen()) {
                port.closePort();
            }
        } catch (Exception ignored) {
        }

        Platform.runLater(() ->
                state.setDisconnected("Disconnected"));
    }

    @Override
    public boolean isConnected() {
        return port != null && port.isOpen();
    }

    // ────────────────────────────────────────────────────────────────
    // Helpers
    // ────────────────────────────────────────────────────────────────
    private void generateWavelengths() {
        double startNm = 189.5;
        double nmPerPixel = 0.48;
        for (int i = 0; i < DATA_POINTS; i++) {
            data.wavelength[i] = startNm + nmPerPixel * i;
        }
    }
}