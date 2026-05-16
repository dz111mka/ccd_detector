package by.spectrometer.ui.manager;

import by.spectrometer.model.ChartScale;
import by.spectrometer.ui.SpectrumChart;
import javafx.geometry.Point2D;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.chart.NumberAxis;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

import java.util.ArrayDeque;
import java.util.Deque;

public class ChartZoomManager {

    private final SpectrumChart chart;
    private final NumberAxis xAxis;
    private final NumberAxis yAxis;

    private boolean zoomMode = false;
    private final Deque<ChartScale> backHistory = new ArrayDeque<>();
    private final Deque<ChartScale> forwardHistory = new ArrayDeque<>();

    private Rectangle zoomRect;
    private double dragStartX;
    private double dragStartY;

    private Node plotArea;

    public ChartZoomManager(SpectrumChart chart) {
        this.chart = chart;
        this.xAxis = (NumberAxis) chart.getXAxis();
        this.yAxis = (NumberAxis) chart.getYAxis();
        initializeZoom();
    }

    public void setZoomMode(boolean enabled) {
        zoomMode = enabled;
        chart.setCursor(enabled ? Cursor.CROSSHAIR : Cursor.DEFAULT);
    }

    public boolean isZoomMode() {
        return zoomMode;
    }

    public void zoomBack() {
        if (backHistory.isEmpty()) return;

        forwardHistory.push(currentScale());
        applyScale(backHistory.pop());
    }

    public void zoomForward() {
        if (forwardHistory.isEmpty()) return;

        backHistory.push(currentScale());
        applyScale(forwardHistory.pop());
    }

    private void initializeZoom() {
        zoomRect = new Rectangle();
        zoomRect.setManaged(false);
        zoomRect.setVisible(false);
        zoomRect.setStroke(Color.DODGERBLUE);
        zoomRect.setFill(Color.web("#1e90ff33"));
        zoomRect.setStrokeWidth(1.5);

        chart.addPlotChild(zoomRect);

        javafx.application.Platform.runLater(() -> {
            plotArea = chart.lookup(".chart-plot-background");
            if (plotArea != null) {
                plotArea.setOnMousePressed(this::onZoomStart);
                plotArea.setOnMouseDragged(this::onZoomDrag);
                plotArea.setOnMouseReleased(this::onZoomEnd);
            }
        });
    }

    private void onZoomStart(MouseEvent e) {
        if (!zoomMode || e.getButton() != MouseButton.PRIMARY) return;

        Point2D p = toPlotArea(e);

        dragStartX = p.getX();
        dragStartY = p.getY();

        zoomRect.setX(dragStartX);
        zoomRect.setY(dragStartY);
        zoomRect.setWidth(0);
        zoomRect.setHeight(0);
        zoomRect.setVisible(true);
    }

    private void onZoomDrag(MouseEvent e) {
        if (!zoomMode || !zoomRect.isVisible()) return;

        Point2D p = toPlotArea(e);

        double x = Math.min(p.getX(), dragStartX);
        double y = Math.min(p.getY(), dragStartY);

        zoomRect.setX(x);
        zoomRect.setY(y);
        zoomRect.setWidth(Math.abs(p.getX() - dragStartX));
        zoomRect.setHeight(Math.abs(p.getY() - dragStartY));
    }

    private void onZoomEnd(MouseEvent e) {
        if (!zoomMode || !zoomRect.isVisible()) return;

        zoomRect.setVisible(false);

        if (zoomRect.getWidth() < 10 || zoomRect.getHeight() < 10) return;

        saveBeforeScaleChange();
        applyZoomFromRect();
    }

    private void applyZoomFromRect() {
        double xMin = xAxis.getValueForDisplay(zoomRect.getX()).doubleValue();
        double xMax = xAxis.getValueForDisplay(
                zoomRect.getX() + zoomRect.getWidth()
        ).doubleValue();

        double yMax = yAxis.getValueForDisplay(zoomRect.getY()).doubleValue();
        double yMin = yAxis.getValueForDisplay(
                zoomRect.getY() + zoomRect.getHeight()
        ).doubleValue();

        xAxis.setAutoRanging(false);
        yAxis.setAutoRanging(false);

        xAxis.setLowerBound(xMin);
        xAxis.setUpperBound(xMax);
        yAxis.setLowerBound(yMin);
        yAxis.setUpperBound(yMax);
    }

    private ChartScale currentScale() {
        return new ChartScale(
                xAxis.getLowerBound(), xAxis.getUpperBound(),
                yAxis.getLowerBound(), yAxis.getUpperBound()
        );
    }

    private void applyScale(ChartScale scale) {
        xAxis.setAutoRanging(false);
        yAxis.setAutoRanging(false);

        xAxis.setLowerBound(scale.xMin());
        xAxis.setUpperBound(scale.xMax());
        yAxis.setLowerBound(scale.yMin());
        yAxis.setUpperBound(scale.yMax());
    }

    private Point2D toPlotArea(MouseEvent e) {
        return plotArea.sceneToLocal(e.getSceneX(), e.getSceneY());
    }

    private void saveBeforeScaleChange() {
        backHistory.push(currentScale());
        forwardHistory.clear();
    }
}