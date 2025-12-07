package by.spectrometer.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class JsonUtils {
    public static final ObjectMapper MAPPER = new ObjectMapper();

    public static void copy(JsonNode node, double[] array) {
        for (int i = 0; i < array.length; i++) {
            array[i] = node.get(i).asDouble();
        }
    }
}