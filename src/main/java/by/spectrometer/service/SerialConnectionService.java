package by.spectrometer.service;

import by.spectrometer.model.ConnectionState;
import by.spectrometer.model.SpectrumData;
import com.fazecast.jSerialComm.SerialPort;
import javafx.application.Platform;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class SerialConnectionService extends ConnectionService {

    private static final int DATA_POINTS       = 3648;
    private static final int BUFFER_SIZE_12BIT = 7296;   // 3648 * 2
    private static final int BUFFER_SIZE_8BIT  = 3648;

    private static final long FRAME_DELAY_MS   = 250;    // ← ключевая задержка, можно менять

    private SerialPort port;
    private InputStream in;
    private OutputStream out;

    private Thread readThread;
    private Thread processThread;
    private volatile boolean running = false;

    private final List<Byte> dataBuffer = new ArrayList<>(BUFFER_SIZE_12BIT + 8192);

    private int adcMode = 0; // 0 = 12-bit, 1 = 8-bit

    public SerialConnectionService(SpectrumData data, ConnectionState state, Runnable onNewSpectrum) {
        super(data, state, onNewSpectrum);
    }

    @Override
    public void connect(String portName) {
        disconnect();

        try {
            port = SerialPort.getCommPort(portName);

            port.setBaudRate(921600);
            port.setNumDataBits(8);
            port.setNumStopBits(1);
            port.setParity(SerialPort.NO_PARITY);

            port.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 100, 0);

            port.setDTR();
            port.setRTS();

            if (!port.openPort()) {
                state.setDisconnected("Не удалось открыть порт");
                return;
            }

            in = port.getInputStreamWithSuppressedTimeoutExceptions();
            out = port.getOutputStream();

            running = true;
            startReadThread();
            startProcessThread();

            Platform.runLater(() -> state.setConnected("USB CCD: " + portName));

            sendCommand("START_12BIT");

        } catch (Exception e) {
            LogService.error("Ошибка подключения к " + portName, e);
            Platform.runLater(() -> state.setDisconnected(e.getMessage()));
        }
    }

    private void startReadThread() {
        readThread = new Thread(() -> {
            byte[] chunk = new byte[4096];
            while (running) {
                try {
                    int bytesRead = in.read(chunk);
                    if (bytesRead <= 0) continue;

                    synchronized (dataBuffer) {
                        for (int i = 0; i < bytesRead; i++) {
                            dataBuffer.add(chunk[i]);
                        }
                    }
                } catch (IOException e) {
                    if (running) LogService.error("Ошибка чтения из порта", e);
                }
            }
        }, "serial-read-thread");

        readThread.setDaemon(true);
        readThread.start();
    }

    private void startProcessThread() {
        processThread = new Thread(() -> {
            while (running) {
                processBufferData();
                try {
                    Thread.sleep(8);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
        }, "serial-process-thread");

        processThread.setDaemon(true);
        processThread.start();
    }

    private void processBufferData() {
        synchronized (dataBuffer) {
            int count = dataBuffer.size();

            if (count >= BUFFER_SIZE_12BIT) {
                LogService.log(String.format(
                        "Frame %d byte | start: %02X %02X %02X %02X | end: %02X %02X %02X %02X",
                        count,
                        dataBuffer.get(0) & 0xFF, dataBuffer.get(1) & 0xFF,
                        dataBuffer.get(2) & 0xFF, dataBuffer.get(3) & 0xFF,
                        dataBuffer.get(count - 4) & 0xFF, dataBuffer.get(count - 3) & 0xFF,
                        dataBuffer.get(count - 2) & 0xFF, dataBuffer.get(count - 1) & 0xFF
                ));
            }

            if (adcMode == 0 && count >= BUFFER_SIZE_12BIT) {

                // Мягкая ресинхронизация (если high-байт > 0x0F — пропускаем по байту)
                while (count >= 2 && (dataBuffer.get(1) & 0xFF) > 0x0F) {
                    dataBuffer.removeFirst();
                    count = dataBuffer.size();
                    LogService.log("Пропущен байт (high > 0x0F), новый размер: " + count);
                }

                if (count < BUFFER_SIZE_12BIT) return;

                // Чтение low first
                for (int i = 0; i < DATA_POINTS; i++) {
                    int lo = dataBuffer.get(2 * i)     & 0xFF;
                    int hi = dataBuffer.get(2 * i + 1) & 0xFF;
                    data.raw[i] = (hi << 8) | lo;
                }

                generateWavelengths();
                dataBuffer.subList(0, BUFFER_SIZE_12BIT).clear();

                Platform.runLater(onNewSpectrum);

                sendCommand("START_12BIT");

                // Задержка — ключ к стабильности
                try {
                    Thread.sleep(FRAME_DELAY_MS);
                } catch (InterruptedException ignored) {}
            }
            else if (adcMode == 1 && count >= BUFFER_SIZE_8BIT) {

                for (int i = 0; i < DATA_POINTS; i++) {
                    data.raw[i] = (dataBuffer.get(i) & 0xFF) << 4;
                }

                generateWavelengths();
                dataBuffer.subList(0, BUFFER_SIZE_8BIT).clear();

                Platform.runLater(onNewSpectrum);
            }

            if (count > 20000) {
                LogService.log("Buffer over (" + count + " byte) → reset");
                dataBuffer.clear();
            }
        }
    }

    @Override
    public void sendCommand(String commandStr) {
        byte cmd = switch (commandStr.toUpperCase()) {
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

            synchronized (dataBuffer) {
                dataBuffer.clear();
            }

            LogService.log("Command is send: " + commandStr);

        } catch (IOException e) {
            LogService.error("Error of send command " + commandStr, e);
        }
    }

    @Override
    public void disconnect() {
        running = false;

        try {
            if (readThread != null) readThread.interrupt();
            if (processThread != null) processThread.interrupt();
            if (port != null && port.isOpen()) {
                port.closePort();
            }
        } catch (Exception ignored) {}

        Platform.runLater(() -> state.setDisconnected("Disconnected"));
    }

    @Override
    public boolean isConnected() {
        return port != null && port.isOpen();
    }

    private void generateWavelengths() {
        double startNm = 189.5;
        double nmPerPixel = 0.48;
        for (int i = 0; i < DATA_POINTS; i++) {
            data.wavelength[i] = startNm + nmPerPixel * i;
        }
    }
}