package by.spectrometer.manager;

import by.spectrometer.model.ConnectionType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;

import java.util.prefs.Preferences;

public class ConfigurationManager {

    private static final String PREF_CONNECTION_TYPE = "connectionType";
    private static final String PREF_WS_ADDRESS = "wsAddress";
    private static final String PREF_DARK_THEME = "darkTheme";

    private final Preferences preferences;

    public ConfigurationManager(Class<?> clazz) {
        this.preferences = Preferences.userNodeForPackage(clazz);
    }

    public ConnectionType getConnectionType() {
        return ConnectionType.valueOf(
                preferences.get(PREF_CONNECTION_TYPE, "WEBSOCKET")
        );
    }

    public void setConnectionType(ConnectionType type) {
        preferences.put(PREF_CONNECTION_TYPE, type.name());
    }

    public String getWebSocketAddress() {
        return preferences.get(PREF_WS_ADDRESS, "192.168.1.77:81");
    }

    public void setWebSocketAddress(String address) {
        preferences.put(PREF_WS_ADDRESS, address);
    }

    public boolean isDarkTheme() {
        return preferences.getBoolean(PREF_DARK_THEME, false);
    }

    public void setDarkTheme(boolean darkTheme) {
        preferences.putBoolean(PREF_DARK_THEME, darkTheme);
    }

    public void loadConnectionConfiguration(ComboBox<ConnectionType> cbConnectionType, TextField tfAddress) {
        ConnectionType type = getConnectionType();
        cbConnectionType.setValue(type);

        if (type == ConnectionType.WEBSOCKET) {
            tfAddress.setText(getWebSocketAddress());
        }
    }

    public void saveConnectionConfiguration(ConnectionType type, String wsAddress) {
        setConnectionType(type);
        if (type == ConnectionType.WEBSOCKET) {
            setWebSocketAddress(wsAddress);
        }
    }
}