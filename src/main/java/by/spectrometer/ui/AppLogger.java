package by.spectrometer.ui;

import javafx.application.Platform;
import javafx.scene.control.TextArea;

public class AppLogger {

    private final TextArea textArea;
    private int maxLines = 1500;           // лимит строк (опционально)
    private boolean autoScroll = true;

    public AppLogger(TextArea textArea) {
        this.textArea = textArea;
        textArea.setEditable(false);
        textArea.setWrapText(true);
    }

    public void log(String message) {
        log(message, null);
    }

    public void log(String message, String prefix) {
        String line = (prefix != null ? prefix + " " : "") + message + "\n";
        Platform.runLater(() -> {
            textArea.appendText(line);
            trimIfNeeded();
            if (autoScroll) {
                textArea.setScrollTop(Double.MAX_VALUE);   // прокрутка вниз
                // или textArea.positionCaret(textArea.getLength());
            }
        });
    }

    public void info(String msg) {
        log(msg, "[INFO]");
    }

    public void warn(String msg) {
        log(msg, "[WARN]");
    }

    public void error(String msg) {
        log(msg, "[ERROR]");
    }

    public void error(String msg, Throwable ex) {
        log(msg + " → " + ex, "[ERROR]");
        // можно добавить stack trace, если нужно
    }

    public void command(String cmd) {
        log(cmd, "→");
    }

    public void received(String data) {
        log(data.trim(), "←");
    }

    private void trimIfNeeded() {
        if (maxLines <= 0) return;
        String text = textArea.getText();
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') count++;
            if (count > maxLines) {
                int cutPos = text.indexOf('\n', i) + 1;
                textArea.setText(text.substring(cutPos));
                return;
            }
        }
    }

    public void clear() {
        Platform.runLater(textArea::clear);
    }

    public void setAutoScroll(boolean value) {
        this.autoScroll = value;
    }
}