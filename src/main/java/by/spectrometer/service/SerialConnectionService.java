package by.spectrometer.service;

import by.spectrometer.model.ConnectionState;
import by.spectrometer.model.SpectrumData;
import com.fazecast.jSerialComm.SerialPort;
import javafx.application.Platform;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class SerialConnectionService extends ConnectionService {

    private SerialPort port;
    private InputStream in;
    private OutputStream out;

    private Thread readThread;
    private Thread processThread;
    private volatile boolean running = false;

    // Буфер как в C#
    private final List<Byte> buffer = new ArrayList<>(8000);

    // === Константы из C# ===
    private static final int DATA_POINTS = 3648;
    private static final int BUFFER_12BIT = 7296; // 3648 * 2
    private static final int BUFFER_8BIT  = 3648;

    // режим АЦП
    private int adcMode = 0; // 0 = 12bit, 1 = 8bit

    public SerialConnectionService(
            SpectrumData data,
            ConnectionState state,
            Runnable onNewSpectrum) {
        super(data, state, onNewSpectrum);
    }

    // =========================================================
    // CONNECT
    // =========================================================
    @Override
    public void connect(String portName) {
        disconnect();

        try {
            port = SerialPort.getCommPort(portName);

            port.setBaudRate(921600);
            port.setNumDataBits(8);
            port.setNumStopBits(1);
            port.setParity(SerialPort.NO_PARITY);

            port.setComPortTimeouts(
                    SerialPort.TIMEOUT_READ_SEMI_BLOCKING,   // ← главное изменение
                    100,                                     // 100 мс — ждём хотя бы 1 байт
                    0                                        // write timeout не важен
            );

            port.setDTR();
            port.setRTS();

            if (!port.openPort()) {
                state.setDisconnected("Не удалось открыть порт");
                return;
            }

            in = port.getInputStreamWithSuppressedTimeoutExceptions();
            out = port.getOutputStream();

            running = true;
            startReader();
            startProcessor();

            Platform.runLater(() ->
                    state.setConnected("USB CCD: " + portName));

        } catch (Exception e) {
            Platform.runLater(() ->
                    state.setDisconnected(e.getMessage()));
        }

        if (port.isOpen()) {
            // Автоматически запускаем 12-битный режим (как по умолчанию в C#)
            sendCommand("START_12BIT");   // → отправит 0xA1
            // или sendCommand("INT_1");   // если нужно сразу задать время интегрирования
        }
    }

    // =========================================================
    // READ THREAD (аналог SerialPort.DataReceived)
    // =========================================================
    private void startReader() {
        readThread = new Thread(() -> {
            byte[] buf = new byte[4096];
            try {
                while (running) {
                    int n = in.read(buf);

                    if (n == -1) {
                        LogService.log("in.read() вернул -1 → конец потока / порт закрыт");
                        running = false;
                        break;
                    }

                    if (n == 0) {
                        // Это нормально при non-blocking, но если постоянно — проблема
                        LogService.log("in.read() returned 0 bytes (w/o data)");
                        Thread.sleep(10); // чтобы не спамило лог
                        continue;
                    }

                    if (n > 0) {
                        LogService.log("read " + n + " bytes");
                        synchronized (buffer) {
                            for (int i = 0; i < n; i++) {
                                buffer.add(buf[i]);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                LogService.error("error in readThread", e);
                running = false;
            }
        }, "serial-reader");
        readThread.setDaemon(true);
        readThread.start();
    }

    // =========================================================
    // PROCESS THREAD (аналог ProcessData)
    // =========================================================
    private void startProcessor() {
        processThread = new Thread(() -> {
            while (running) {
                processBuffer();
                try {
                    Thread.sleep(5);
                } catch (InterruptedException ignored) {}
            }
        }, "serial-processor");

        processThread.setDaemon(true);
        processThread.start();
    }

    // =========================================================
    // BUFFER PROCESSING (1:1 C#)
    // =========================================================
    private void processBuffer() {
        synchronized (buffer) {
            int size = buffer.size();

            // Логируем размер только если он изменился существенно или близко к цели
            if (size >= 7000 || size % 500 == 0 || size == 0) {
                LogService.log("the buffer contains " + size + " bytes");
            }

            if (adcMode == 0 && size >= 7295) {  // ← пробуем 7295
                LogService.log("!!! PROCESSED 12-bit frame !!! size = " + size);
                LogService.log("First 16 bytes (hex): " + getHexDump(buffer, 0, 16));
                LogService.log("Last 16 bytes (hex): " + getHexDump(buffer, size - 16, 16));

                for (int i = 1; i < DATA_POINTS; i++) {
                    int lo = buffer.get(2 * i - 1) & 0xFF;     // младший байт первый (как часто в прошивках)
                    int hi = buffer.get(2 * i)     & 0xFF;     // старший байт
                    data.raw[i] = (hi << 8) | lo;
                }
                generateWavelengths();
                buffer.clear();
                Platform.runLater(onNewSpectrum);

                sendCommand("START_12BIT");  // запуск следующего кадра сразу
                LogService.log("Запрос следующего кадра отправлен");
            } else if (size > 8000) {
                LogService.log("Buffer overflow (" + size + "), clearing");
                buffer.clear();
            }
        }
    }

    private String getHexDump(List<Byte> buf, int start, int len) {
        StringBuilder sb = new StringBuilder();
        int end = Math.min(start + len, buf.size());
        for (int i = start; i < end; i++) {
            sb.append(String.format("%02X ", buf.get(i)));
        }
        return sb.toString().trim();
    }

    // =========================================================
    // COMMANDS (байтовый протокол)
    // =========================================================
    @Override
    public void sendCommand(String command) {
        try {
            byte cmd;

            switch (command) {
                case "START_12BIT":
                    cmd = (byte) 0xA1;
                    adcMode = 0;
                    break;

                case "START_8BIT":
                    cmd = (byte) 0xA2;
                    adcMode = 1;
                    break;

                case "STATS":
                    cmd = (byte) 0xA3;
                    break;

                case "INT_1": cmd = (byte) 0xB1; break;
                case "INT_2": cmd = (byte) 0xB2; break;
                case "INT_3": cmd = (byte) 0xB3; break;
                case "INT_4": cmd = (byte) 0xB4; break;
                case "INT_5": cmd = (byte) 0xB5; break;
                case "INT_6": cmd = (byte) 0xB6; break;
                case "INT_7": cmd = (byte) 0xB7; break;
                case "INT_8": cmd = (byte) 0xB8; break;
                case "INT_9": cmd = (byte) 0xB9; break;
                case "INT_10": cmd = (byte) 0xBA; break;

                default:
                    return;
            }

            out.write(cmd);
            out.flush();

            synchronized (buffer) {
                buffer.clear();
            }

        } catch (Exception ignored) {}
    }

    // =========================================================
    // DISCONNECT
    // =========================================================
    @Override
    public void disconnect() {
        running = false;

        try {
            if (readThread != null) readThread.interrupt();
            if (processThread != null) processThread.interrupt();
            if (port != null && port.isOpen()) port.closePort();
        } catch (Exception ignored) {}

        Platform.runLater(() ->
                state.setDisconnected("Отключено"));
    }

    @Override
    public boolean isConnected() {
        return port != null && port.isOpen();
    }

    // =========================================================
    // WAVELENGTH GENERATION (как у тебя)
    // =========================================================
    private void generateWavelengths() {
        double a = 189.5;     // начало, нм
        double b = 0.48;      // нм/пиксель (пример!)
        for (int i = 0; i < DATA_POINTS; i++) {
            data.wavelength[i] = a + b * i;
        }
    }
}