package by.spectrometer.manager;

import by.spectrometer.controller.SpectrometerController;
import by.spectrometer.util.Constants;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

import java.util.prefs.Preferences;

public class ThemeManager {

    private final SpectrometerController controller;
    private final VBox view;
    private final MenuBar menuBar;

    private boolean isDarkTheme = false;

    public ThemeManager(SpectrometerController controller, VBox view, MenuBar menuBar) {
        this.controller = controller;
        this.view = view;
        this.menuBar = menuBar;
    }

    public void toggleTheme() {
        isDarkTheme = !isDarkTheme;
        applyTheme();
        saveThemeConfiguration();
    }

    public void applyTheme() {
        // Цвета для текущей темы
        String bgColor, panelBg, textColor, borderColor, buttonBg, buttonHover;

        if (isDarkTheme) {
            bgColor = Constants.DarkTheme.BACKGROUND;
            panelBg = Constants.DarkTheme.PANEL_BACKGROUND;
            textColor = Constants.DarkTheme.TEXT_COLOR;
            borderColor = Constants.DarkTheme.BORDER_COLOR;
            buttonBg = Constants.DarkTheme.BUTTON_BACKGROUND;
            buttonHover = Constants.DarkTheme.BUTTON_HOVER;
        } else {
            bgColor = Constants.LightTheme.BACKGROUND;
            panelBg = Constants.LightTheme.PANEL_BACKGROUND;
            textColor = Constants.LightTheme.TEXT_COLOR;
            borderColor = Constants.LightTheme.BORDER_COLOR;
            buttonBg = Constants.LightTheme.BUTTON_BACKGROUND;
            buttonHover = Constants.LightTheme.BUTTON_HOVER;
        }

        // Применяем стили к основным контейнерам
        view.setStyle("-fx-background-color: " + bgColor + ";");

        // Применяем стили к меню
        menuBar.setStyle("-fx-background-color: " + panelBg + "; " +
                "-fx-text-fill: " + textColor + ";");

        // Применяем стили к контроллерам
        applyStylesToAllChildren(view, panelBg, textColor, buttonBg, borderColor);

        // Обновляем график
        controller.getChart().applyTheme(isDarkTheme);
    }

    private void applyStylesToAllChildren(Node node, String bgColor, String textColor, String buttonColor, String borderColor) {
        if (node instanceof Control) {
            Control control = (Control) node;

            if (node instanceof Button) {
                control.setStyle("-fx-background-color: " + buttonColor + "; " +
                        "-fx-text-fill: " + textColor + "; " +
                        "-fx-border-color: " + borderColor + "; " +
                        "-fx-border-width: 1px; " +
                        "-fx-border-radius: 4px;");
            } else if (node instanceof TextField) {
                control.setStyle("-fx-background-color: " + bgColor + "; " +
                        "-fx-text-fill: " + textColor + "; " +
                        "-fx-prompt-text-fill: " + (isDarkTheme ? "#888888" : "#999999") + "; " +
                        "-fx-border-color: " + borderColor + "; " +
                        "-fx-border-width: 1px; " +
                        "-fx-border-radius: 4px;");
            } else if (node instanceof ComboBox) {
                control.setStyle("-fx-background-color: " + buttonColor + "; " +
                        "-fx-text-fill: " + textColor + "; " +
                        "-fx-border-color: " + borderColor + "; " +
                        "-fx-border-width: 1px; " +
                        "-fx-border-radius: 4px;");
            } else if (node instanceof Label) {
                control.setStyle("-fx-text-fill: " + textColor + ";");
            } else if (node instanceof ListView) {
                control.setStyle("-fx-background-color: " + bgColor + "; " +
                        "-fx-text-fill: " + textColor + "; " +
                        "-fx-border-color: " + borderColor + "; " +
                        "-fx-border-width: 1px; " +
                        "-fx-border-radius: 4px;");
            }
        }

        if (node instanceof Parent) {
            for (Node child : ((Parent) node).getChildrenUnmodifiable()) {
                applyStylesToAllChildren(child, bgColor, textColor, buttonColor, borderColor);
            }
        }
    }

    public void loadThemeConfiguration() {
        Preferences prefs = Preferences.userNodeForPackage(getClass());
        isDarkTheme = prefs.getBoolean("darkTheme", false);
        applyTheme();
    }

    public void saveThemeConfiguration() {
        Preferences prefs = Preferences.userNodeForPackage(getClass());
        prefs.putBoolean("darkTheme", isDarkTheme);
    }

    public boolean isDarkTheme() {
        return isDarkTheme;
    }
}