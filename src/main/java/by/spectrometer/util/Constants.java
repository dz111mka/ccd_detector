package by.spectrometer.util;

public class Constants {

    // ────────────────────────────────────────────────────────────────
    // Константы
    // ────────────────────────────────────────────────────────────────
    public static final long REDRAW_INTERVAL_MS = 100;
    public static final int TRANSMISSION_POS_MOTOR1 = 0;
    public static final int TRANSMISSION_POS_MOTOR2 = 0;
    public static final int REFLECTION_BASE_POS_MOTOR1 = 90;
    public static final int REFLECTION_BASE_POS_MOTOR2 = 135;
    public static final int FINE_ADJUSTMENT_MIN = -45;
    public static final int FINE_ADJUSTMENT_MAX = 45;
    public static final int SMOOTHING_WINDOW_SIZE = 5;
    public static final int TRANSMISSION_BUFFER_FRAMES = 10;

    // ────────────────────────────────────────────────────────────────
    // Интеграция (SH period / exposure time)
    // Значения correspond to firmware commands INT_1 .. INT_10
    // Integration time t_int = SH_period / 2 MHz
    // ────────────────────────────────────────────────────────────────
    public static final int SH_PERIOD_10US = 20;    // INT_1
    public static final int SH_PERIOD_20US = 40;    // INT_2
    public static final int SH_PERIOD_50US = 100;   // INT_3
    public static final int SH_PERIOD_60US = 120;   // INT_4
    public static final int SH_PERIOD_75US = 150;   // INT_5
    public static final int SH_PERIOD_100US = 200;   // INT_6
    public static final int SH_PERIOD_500US = 1000;  // INT_7
    public static final int SH_PERIOD_1250US = 2500;  // INT_8
    public static final int SH_PERIOD_2500US = 5000;  // INT_9
    public static final int SH_PERIOD_7500US = 15000; // INT_10

    // ────────────────────────────────────────────────────────────────
    // Цвета для тем
    // ────────────────────────────────────────────────────────────────
    public static class LightTheme {
        public static final String BACKGROUND = "#f4f4f4";
        public static final String PANEL_BACKGROUND = "#ffffff";
        public static final String TEXT_COLOR = "#000000";
        public static final String BORDER_COLOR = "#cccccc";
        public static final String BUTTON_BACKGROUND = "#e0e0e0";
        public static final String BUTTON_HOVER = "#d0d0d0";
        public static final String CHART_BACKGROUND = "#ffffff";
        public static final String CHART_GRID = "#e0e0e0";
        public static final String CHART_AXIS = "#666666";
        public static final String SPECTRUM_LINE = "#2196F3";
    }

    public static class DarkTheme {
        public static final String BACKGROUND = "#2d2d2d";
        public static final String PANEL_BACKGROUND = "#3d3d3d";
        public static final String TEXT_COLOR = "#ffffff";
        public static final String BORDER_COLOR = "#555555";
        public static final String BUTTON_BACKGROUND = "#4d4d4d";
        public static final String BUTTON_HOVER = "#5d5d5d";
        public static final String CHART_BACKGROUND = "#3d3d3d";
        public static final String CHART_GRID = "#555555";
        public static final String CHART_AXIS = "#aaaaaa";
        public static final String SPECTRUM_LINE = "#4CAF50";
    }
}
