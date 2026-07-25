package by.spectrometer.ui.manager;

import by.spectrometer.ui.SpectrumChart;
import by.spectrometer.util.Constants;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.chart.NumberAxis;
import javafx.scene.control.Label;

import java.util.Set;

public class ChartThemeManager {

    private final SpectrumChart chart;

    public ChartThemeManager(SpectrumChart chart) {
        this.chart = chart;
    }

    public void applyTheme(boolean isDarkTheme) {
        String bgColor, gridColor, axisColor, spectrumLineColor;

        if (isDarkTheme) {
            bgColor = Constants.DarkTheme.CHART_BACKGROUND;
            gridColor = Constants.DarkTheme.CHART_GRID;
            axisColor = Constants.DarkTheme.CHART_AXIS;
            spectrumLineColor = Constants.DarkTheme.SPECTRUM_LINE;
        } else {
            bgColor = Constants.LightTheme.CHART_BACKGROUND;
            gridColor = Constants.LightTheme.CHART_GRID;
            axisColor = Constants.LightTheme.CHART_AXIS;
            spectrumLineColor = Constants.LightTheme.SPECTRUM_LINE;
        }

        chart.setStyle("-fx-background-color: " + bgColor + ";");

        NumberAxis xAxis = (NumberAxis) chart.getXAxis();
        NumberAxis yAxis = (NumberAxis) chart.getYAxis();

        xAxis.setStyle("-fx-tick-label-fill: " + axisColor + "; " +
                "-fx-label-fill: " + axisColor + "; " +
                "-fx-axis-line-color: " + axisColor + "; " +
                "-fx-tick-mark-color: " + axisColor + ";");

        yAxis.setStyle("-fx-tick-label-fill: " + axisColor + "; " +
                "-fx-label-fill: " + axisColor + "; " +
                "-fx-axis-line-color: " + axisColor + "; " +
                "-fx-tick-mark-color: " + axisColor + ";");

        Platform.runLater(() -> {
            Node plotBackground = chart.lookup(".chart-plot-background");
            if (plotBackground != null) {
                plotBackground.setStyle("-fx-background-color: " + bgColor + ";");
            }

            Node grid = chart.lookup(".chart-grid-lines");
            if (grid != null) {
                grid.setStyle("-fx-stroke: " + gridColor + ";");
            }

            Node spectrumLine = chart.getSpectrumSeries().getNode().lookup(".chart-series-line");
            if (spectrumLine != null) {
                spectrumLine.setStyle("-fx-stroke: " + spectrumLineColor + "; -fx-stroke-width: 1.5px;");
            }

            updateMinimaSeriesStyle(isDarkTheme);
            updatePeaksSeriesStyle(isDarkTheme);
            updateBaselineSeriesStyle(isDarkTheme);
            updateLegendStyle(isDarkTheme);
        });
    }

    private void updateLegendStyle(boolean isDarkTheme) {
        String legendBg = isDarkTheme ? Constants.DarkTheme.PANEL_BACKGROUND : Constants.LightTheme.PANEL_BACKGROUND;
        String textColor = isDarkTheme ? Constants.DarkTheme.TEXT_COLOR : Constants.LightTheme.TEXT_COLOR;
        String borderColor = isDarkTheme ? Constants.DarkTheme.BORDER_COLOR : Constants.LightTheme.BORDER_COLOR;

        Node legend = chart.lookup(".chart-legend");
        if (legend != null) {
            legend.setStyle("-fx-background-color: " + legendBg + "; " +
                    "-fx-border-color: " + borderColor + "; " +
                    "-fx-border-width: 1px; " +
                    "-fx-padding: 6px;");
            applyLegendTextColor(legend, textColor);
        }

        chart.lookupAll(".chart-legend-item").forEach(item ->
                item.setStyle("-fx-text-fill: " + textColor + ";")
        );
    }

    private void applyLegendTextColor(Node node, String textColor) {
        if (node instanceof Label label) {
            label.setStyle("-fx-text-fill: " + textColor + ";");
        }

        if (node instanceof Parent parent) {
            parent.getChildrenUnmodifiable().forEach(child -> applyLegendTextColor(child, textColor));
        }
    }

    private void updateMinimaSeriesStyle(boolean isDarkTheme) {
        chart.getMinimaSeries().nodeProperty().addListener((obs, oldNode, newNode) -> {
            if (newNode != null) {
                Platform.runLater(() -> {
                    Node line = newNode.lookup(".chart-series-line");
                    if (line != null) {
                        line.setStyle("-fx-stroke: transparent;");
                    }

                    Set<Node> symbols = newNode.lookupAll(".chart-series-symbol");
                    for (Node symbol : symbols) {
                        String borderColor = isDarkTheme ? "#333333" : "white";
                        symbol.setStyle("-fx-background-color: red; " +
                                "-fx-background-radius: 5px; " +
                                "-fx-padding: 5px; " +
                                "-fx-border-color: " + borderColor + "; " +
                                "-fx-border-width: 2px; " +
                                "-fx-border-radius: 5px; " +
                                "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.8), 5, 0, 0, 0);");
                        symbol.setVisible(true);
                    }
                });
            }
        });
    }

    private void updatePeaksSeriesStyle(boolean isDarkTheme) {
        chart.getPeaksSeries().nodeProperty().addListener((obs, oldNode, newNode) -> {
            if (newNode != null) {
                Platform.runLater(() -> {
                    Node line = newNode.lookup(".chart-series-line");
                    if (line != null) {
                        line.setStyle("-fx-stroke: transparent;");
                    }

                    Set<Node> symbols = newNode.lookupAll(".chart-series-symbol");
                    for (Node symbol : symbols) {
                        String borderColor = isDarkTheme ? "#333333" : "white";
                        symbol.setStyle("-fx-background-color: green; " +
                                "-fx-background-radius: 5px; " +
                                "-fx-padding: 5px; " +
                                "-fx-border-color: " + borderColor + "; " +
                                "-fx-border-width: 2px; " +
                                "-fx-border-radius: 5px; " +
                                "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.8), 5, 0, 0, 0);");
                        symbol.setVisible(true);
                    }
                });
            }
        });
    }

    private void updateBaselineSeriesStyle(boolean isDarkTheme) {
        String baselineColor = isDarkTheme ? "#ff9800" : "orange";
        chart.getBaselineSeries().getNode().setStyle("-fx-stroke: " + baselineColor + "; -fx-stroke-width: 2px;");
    }
}
