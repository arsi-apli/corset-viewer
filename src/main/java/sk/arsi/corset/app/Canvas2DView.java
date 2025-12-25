package sk.arsi.corset.app;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import sk.arsi.corset.measure.MeasurementUtils;
import sk.arsi.corset.measure.MeasurementUtils.SeamSide;
import sk.arsi.corset.measure.MeasurementUtils.SeamSplit;
import sk.arsi.corset.model.Curve2D;
import sk.arsi.corset.model.PanelCurves;
import sk.arsi.corset.model.PanelId;
import sk.arsi.corset.model.Pt;

import java.util.ArrayList;
import java.util.List;

public final class Canvas2DView {

    private enum LayoutMode {
        TOP,
        WAIST,
        BOTTOM
    }

    private static final class Transform2D {

        private final double angleRad;
        private final double pivotX;
        private final double pivotY;
        private final double tx;
        private final double ty;

        private Transform2D(double angleRad, double pivotX, double pivotY, double tx, double ty) {
            this.angleRad = angleRad;
            this.pivotX = pivotX;
            this.pivotY = pivotY;
            this.tx = tx;
            this.ty = ty;
        }

        private Pt apply(Pt p) {
            if (p == null) {
                return null;
            }
            double x = p.getX();
            double y = p.getY();

            double dx = x - pivotX;
            double dy = y - pivotY;

            double cos = Math.cos(angleRad);
            double sin = Math.sin(angleRad);

            double rx = cos * dx - sin * dy;
            double ry = sin * dx + cos * dy;

            double outX = pivotX + rx + tx;
            double outY = pivotY + ry + ty;
            return new Pt(outX, outY);
        }
    }

    private static final class RenderedPanel {

        private final PanelCurves panel;
        private final Transform2D transform;
        private final Color color;

        private RenderedPanel(PanelCurves panel, Transform2D transform, Color color) {
            this.panel = panel;
            this.transform = transform;
            this.color = color;
        }
    }

    private static final class Bounds {

        private final double minX;
        private final double minY;
        private final double maxX;
        private final double maxY;

        private Bounds(double minX, double minY, double maxX, double maxY) {
            this.minX = minX;
            this.minY = minY;
            this.maxX = maxX;
            this.maxY = maxY;
        }
    }

    private static final class BoundsAcc {

        private double minX;
        private double minY;
        private double maxX;
        private double maxY;

        private BoundsAcc(double minX, double minY, double maxX, double maxY) {
            this.minX = minX;
            this.minY = minY;
            this.maxX = maxX;
            this.maxY = maxY;
        }

        private void addCurve(Curve2D c, Transform2D t) {
            if (c == null || c.getPoints() == null) {
                return;
            }
            List<Pt> pts = c.getPoints();
            for (int i = 0; i < pts.size(); i++) {
                Pt p = pts.get(i);
                if (p == null) {
                    continue;
                }

                Pt q = t.apply(p);
                if (q == null) {
                    continue;
                }

                double x = q.getX();
                double y = q.getY();
                if (!Double.isFinite(x) || !Double.isFinite(y)) {
                    continue;
                }

                if (x < minX) {
                    minX = x;
                }
                if (y < minY) {
                    minY = y;
                }
                if (x > maxX) {
                    maxX = x;
                }
                if (y > maxY) {
                    maxY = y;
                }
            }
        }

        private boolean isValid() {
            return Double.isFinite(minX) && Double.isFinite(minY) && Double.isFinite(maxX) && Double.isFinite(maxY)
                    && minX <= maxX && minY <= maxY;
        }
    }

    // --- Safety ---
    private static final double MAX_CANVAS_TEXTURE_DIM = 16000.0; // safety under 16384 GPU limit

    // --- Fonts: bigger / readable ---
    private static final int FONT_TITLE = 18;
    private static final int FONT_LABEL = 15;
    private static final int FONT_VALUE = 18;
    private static final int FONT_TABLE = 16;

    // --- Measurement panel sizing ---
    private static final double MEASURE_MIN_W = 380;
    private static final double MEASURE_PREF_W = 540;
    private static final double MEASURE_MAX_W = 900;

    private final Canvas canvas;
    private final BorderPane root;
    private final HBox toolbar;

    // host pane for canvas so we bind to center area size (prevents huge textures)
    private final StackPane canvasHost;

    // measurement UI
    private final VBox measurementPanel;
    private final Slider circumferenceSlider;
    private final Label dyLabel;
    private final Label circumferenceLabel;
    private final TextArea seamTableArea;

    private List<PanelCurves> panels;
    private List<RenderedPanel> rendered;

    // view transform (world -> screen)
    private double scale;
    private double offsetX;
    private double offsetY;

    private double lastMouseX;
    private double lastMouseY;
    private boolean dragging;

    private boolean didInitialFit;
    private LayoutMode mode;

    // WAIST mode spacing in "world mm"
    private double waistGapMm;

    // Measurement: height offset from waist in mm
    private double dyMm;

    public Canvas2DView() {
        this.canvas = new Canvas(1200, 700);
        this.root = new BorderPane();
        this.toolbar = new HBox(8.0);

        this.canvasHost = new StackPane(canvas);

        this.measurementPanel = new VBox(10.0);
        this.circumferenceSlider = new Slider(-200.0, 200.0, 0.0);
        this.dyLabel = new Label("dyMm: 0.0 mm");
        this.circumferenceLabel = new Label("Circumference: 0.0 mm");
        this.seamTableArea = new TextArea();

        this.panels = new ArrayList<PanelCurves>();
        this.rendered = new ArrayList<RenderedPanel>();

        this.scale = 2.0;
        this.offsetX = 80.0;
        this.offsetY = 350.0;

        this.dragging = false;
        this.didInitialFit = false;

        this.mode = LayoutMode.TOP;
        this.waistGapMm = 30.0;
        this.dyMm = 0.0;

        initUi();
        bindResize();
        bindInput();

        rebuildLayout();
        redraw();
        updateMeasurements();
    }

    public Node getNode() {
        return root;
    }

    public void setPanels(List<PanelCurves> panels) {
        if (panels == null) {
            this.panels = new ArrayList<PanelCurves>();
        } else {
            this.panels = panels;
        }

        this.didInitialFit = false;
        rebuildLayout();
        fitToContent();
        redraw();
        updateMeasurements();
    }

    private void initUi() {
        // --- Toolbar ---
        Button btnTop = new Button("TOP");
        Button btnWaist = new Button("WAIST");
        Button btnBottom = new Button("BOTTOM");

        btnTop.setOnAction(e -> switchMode(LayoutMode.TOP));
        btnWaist.setOnAction(e -> switchMode(LayoutMode.WAIST));
        btnBottom.setOnAction(e -> switchMode(LayoutMode.BOTTOM));

        toolbar.getChildren().addAll(btnTop, btnWaist, btnBottom);
        toolbar.setPadding(new Insets(8.0));
        root.setTop(toolbar);

        // --- Center ---
        root.setCenter(canvasHost);

        // --- Right: Measurement panel ---
        Label measureTitle = new Label("Measurements");
        measureTitle.setStyle("-fx-font-weight: bold; -fx-font-size: " + FONT_TITLE + "px;");

        Label sliderLabel = new Label("Height from Waist (mm):");
        sliderLabel.setStyle("-fx-font-size: " + FONT_LABEL + "px;");

        circumferenceSlider.setShowTickLabels(true);
        circumferenceSlider.setShowTickMarks(true);
        circumferenceSlider.setMajorTickUnit(50.0);
        circumferenceSlider.setMinorTickCount(4);
        circumferenceSlider.setBlockIncrement(10.0);
        circumferenceSlider.setStyle("-fx-font-size: " + FONT_LABEL + "px;");

        circumferenceSlider.valueProperty().addListener((obs, oldV, newV) -> {
            dyMm = newV.doubleValue();
            updateMeasurements();
        });

        dyLabel.setStyle("-fx-font-size: " + FONT_VALUE + "px; -fx-font-weight: bold;");
        circumferenceLabel.setStyle("-fx-font-size: " + FONT_VALUE + "px; -fx-font-weight: bold;");

        Label seamTitle = new Label("Seam matching (curve length from waist):");
        seamTitle.setStyle("-fx-font-weight: bold; -fx-font-size: " + FONT_LABEL + "px;");

        seamTableArea.setEditable(false);
        seamTableArea.setWrapText(true);
        seamTableArea.setPrefRowCount(18);
        seamTableArea.setStyle("-fx-font-family: monospace; -fx-font-size: " + FONT_TABLE + "px;");

        ScrollPane seamScroll = new ScrollPane(seamTableArea);
        seamScroll.setFitToWidth(true);
        seamScroll.setPrefHeight(520);

        measurementPanel.getChildren().addAll(
                measureTitle,
                sliderLabel,
                circumferenceSlider,
                dyLabel,
                circumferenceLabel,
                seamTitle,
                seamScroll
        );

        measurementPanel.setPadding(new Insets(10.0));
        measurementPanel.setMinWidth(MEASURE_MIN_W);
        measurementPanel.setPrefWidth(MEASURE_PREF_W);
        measurementPanel.setMaxWidth(MEASURE_MAX_W);
        measurementPanel.setStyle("-fx-background-color: #f9f9f9; -fx-border-color: #ccc; -fx-border-width: 0 0 0 1;");

        root.setRight(measurementPanel);
    }

    private void bindResize() {
        canvas.widthProperty().bind(canvasHost.widthProperty());
        canvas.heightProperty().bind(canvasHost.heightProperty());

        // Safety clamp (avoid GPU texture crash)
        canvas.widthProperty().addListener((obs, oldV, newV) -> {
            if (newV != null && newV.doubleValue() > MAX_CANVAS_TEXTURE_DIM) {
                canvas.setWidth(MAX_CANVAS_TEXTURE_DIM);
            }
        });
        canvas.heightProperty().addListener((obs, oldV, newV) -> {
            if (newV != null && newV.doubleValue() > MAX_CANVAS_TEXTURE_DIM) {
                canvas.setHeight(MAX_CANVAS_TEXTURE_DIM);
            }
        });

        canvasHost.widthProperty().addListener((obs, oldV, newV) -> onViewportResize());
        canvasHost.heightProperty().addListener((obs, oldV, newV) -> onViewportResize());
    }

    private void onViewportResize() {
        if (!didInitialFit) {
            fitToContent();
            didInitialFit = true;
        }
        redraw();
    }

    private void bindInput() {
        root.setFocusTraversable(true);
        root.requestFocus();

        root.setOnMousePressed(e -> {
            root.requestFocus();
            if (e.getButton() == MouseButton.PRIMARY) {
                dragging = true;
                lastMouseX = e.getX();
                lastMouseY = e.getY();
            }
        });

        root.setOnMouseReleased(e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                dragging = false;
            }
        });

        root.setOnMouseDragged(e -> {
            if (!dragging) {
                return;
            }
            double dx = e.getX() - lastMouseX;
            double dy = e.getY() - lastMouseY;

            offsetX = offsetX + dx;
            offsetY = offsetY + dy;

            lastMouseX = e.getX();
            lastMouseY = e.getY();

            redraw();
        });

        root.addEventFilter(ScrollEvent.SCROLL, e -> {
            double delta = e.getDeltaY();
            if (delta == 0.0) {
                delta = e.getTextDeltaY();
            }
            if (delta == 0.0) {
                return;
            }

            double factor = delta > 0 ? 1.1 : 1.0 / 1.1;

            double mx = e.getX();
            double my = e.getY();

            double oldScale = scale;
            double newScale = clamp(scale * factor, 0.02, 200.0);

            // zoom to cursor
            double worldX = (mx - offsetX) / oldScale;
            double worldY = (my - offsetY) / oldScale;

            scale = newScale;

            offsetX = mx - worldX * newScale;
            offsetY = my - worldY * newScale;

            redraw();
            e.consume();
        });

        root.setOnKeyPressed(e -> {
            KeyCode code = e.getCode();
            if (code == KeyCode.F) {
                fitToContent();
                redraw();
            } else if (code == KeyCode.DIGIT1) {
                switchMode(LayoutMode.TOP);
            } else if (code == KeyCode.DIGIT2) {
                switchMode(LayoutMode.WAIST);
            } else if (code == KeyCode.DIGIT3) {
                switchMode(LayoutMode.BOTTOM);
            } else if (code == KeyCode.OPEN_BRACKET) {
                waistGapMm = Math.max(0.0, waistGapMm - 5.0);
                if (mode == LayoutMode.WAIST) {
                    rebuildLayout();
                    fitToContent();
                    redraw();
                }
            } else if (code == KeyCode.CLOSE_BRACKET) {
                waistGapMm = waistGapMm + 5.0;
                if (mode == LayoutMode.WAIST) {
                    rebuildLayout();
                    fitToContent();
                    redraw();
                }
            }
        });
    }

    private void switchMode(LayoutMode newMode) {
        mode = newMode;
        didInitialFit = false;
        rebuildLayout();
        fitToContent();
        redraw();
        root.requestFocus();
    }

    /**
     * Build per-panel transforms depending on mode.
     */
    private void rebuildLayout() {
        rendered = new ArrayList<RenderedPanel>();
        if (panels == null || panels.isEmpty()) {
            return;
        }

        if (mode == LayoutMode.WAIST) {
            buildWaistLayout();
        } else if (mode == LayoutMode.TOP) {
            buildEndpointLayout(true);
        } else {
            buildEndpointLayout(false);
        }
    }

    /**
     * TOP or BOTTOM mode: panels touch by endpoints of TOP (or BOTTOM) curve.
     * No rotation; only translation so that next.leftEndpoint lands on
     * prev.rightEndpoint.
     */
    private void buildEndpointLayout(boolean top) {
        double curX = 0.0;
        double curY = 0.0;

        Pt prevRight = null;

        for (int i = 0; i < panels.size(); i++) {
            PanelCurves p = panels.get(i);
            Curve2D edge = top ? p.getTop() : p.getBottom();

            Pt left = extremeByX(edge, true);
            Pt right = extremeByX(edge, false);

            // fallback if missing
            if (left == null || right == null) {
                Transform2D t = new Transform2D(0.0, 0.0, 0.0, curX, curY);
                rendered.add(new RenderedPanel(p, t, colorForIndex(i)));
                curX = curX + 150.0;
                prevRight = null;
                continue;
            }

            double tx;
            double ty;

            if (prevRight == null) {
                tx = curX - left.getX();
                ty = curY - left.getY();
            } else {
                tx = prevRight.getX() - left.getX();
                ty = prevRight.getY() - left.getY();
            }

            Transform2D t = new Transform2D(0.0, 0.0, 0.0, tx, ty);
            rendered.add(new RenderedPanel(p, t, colorForIndex(i)));

            prevRight = t.apply(right);
        }
    }

    /**
     * WAIST mode: rotate each panel so its waist is horizontal, align waist to
     * y=0, and space panels with a gap.
     */
    private void buildWaistLayout() {
        double curX = 0.0;

        for (int i = 0; i < panels.size(); i++) {
            PanelCurves p = panels.get(i);

            Curve2D waist = p.getWaist();
            Pt wLeft = extremeByX(waist, true);
            Pt wRight = extremeByX(waist, false);

            if (wLeft == null || wRight == null) {
                Transform2D tFallback = new Transform2D(0.0, 0.0, 0.0, curX, 0.0);
                rendered.add(new RenderedPanel(p, tFallback, colorForIndex(i)));
                curX = curX + 200.0 + waistGapMm;
                continue;
            }

            double dx = wRight.getX() - wLeft.getX();
            double dy = wRight.getY() - wLeft.getY();
            double angle = Math.atan2(dy, dx);

            double angleRad = -angle;
            double pivotX = wLeft.getX();
            double pivotY = wLeft.getY();

            Transform2D t0 = new Transform2D(angleRad, pivotX, pivotY, -pivotX, -pivotY);
            Bounds b0 = computePanelBounds(p, t0);

            double tx = (-pivotX) + (curX - b0.minX);
            double ty = (-pivotY);

            Transform2D t = new Transform2D(angleRad, pivotX, pivotY, tx, ty);
            rendered.add(new RenderedPanel(p, t, colorForIndex(i)));

            Bounds b = computePanelBounds(p, t);
            double width = Math.max(1.0, b.maxX - b.minX);

            curX = curX + width + waistGapMm;
        }
    }

    private Bounds computePanelBounds(PanelCurves p, Transform2D t) {
        BoundsAcc acc = new BoundsAcc(
                Double.POSITIVE_INFINITY,
                Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY,
                Double.NEGATIVE_INFINITY
        );

        acc.addCurve(p.getTop(), t);
        acc.addCurve(p.getBottom(), t);
        acc.addCurve(p.getWaist(), t);

        acc.addCurve(p.getSeamToPrevUp(), t);
        acc.addCurve(p.getSeamToPrevDown(), t);
        acc.addCurve(p.getSeamToNextUp(), t);
        acc.addCurve(p.getSeamToNextDown(), t);

        if (!acc.isValid()) {
            return new Bounds(0.0, 0.0, 200.0, 200.0);
        }

        return new Bounds(acc.minX, acc.minY, acc.maxX, acc.maxY);
    }

    private void redraw() {
        GraphicsContext g = canvas.getGraphicsContext2D();

        double w = canvas.getWidth();
        double h = canvas.getHeight();

        g.setFill(Color.WHITE);
        g.fillRect(0, 0, w, h);

        // axes
        g.setStroke(Color.LIGHTGRAY);
        g.setLineWidth(1.0);
        drawLineWorld(g, -10000, 0, 10000, 0);
        drawLineWorld(g, 0, -10000, 0, 10000);

        // mode label
        g.setFill(Color.GRAY);
        g.fillText("Mode: " + mode + (mode == LayoutMode.WAIST ? (" (gap=" + waistGapMm + "mm)") : ""), 12, 16);

        for (int i = 0; i < rendered.size(); i++) {
            RenderedPanel rp = rendered.get(i);
            Color base = rp.color;

            // seams
            strokeCurve(g, rp, rp.panel.getSeamToPrevUp(), base.darker(), 1.5);
            strokeCurve(g, rp, rp.panel.getSeamToPrevDown(), base.darker(), 1.5);
            strokeCurve(g, rp, rp.panel.getSeamToNextUp(), base.darker(), 1.5);
            strokeCurve(g, rp, rp.panel.getSeamToNextDown(), base.darker(), 1.5);

            // top/bottom
            strokeCurve(g, rp, rp.panel.getTop(), base, 2.0);
            strokeCurve(g, rp, rp.panel.getBottom(), base, 2.0);

            // waist
            strokeCurve(g, rp, rp.panel.getWaist(), Color.BLACK, 3.0);
        }
    }

    private void strokeCurve(GraphicsContext g, RenderedPanel rp, Curve2D curve, Color color, double width) {
        if (curve == null) {
            return;
        }
        List<Pt> pts = curve.getPoints();
        if (pts == null || pts.size() < 2) {
            return;
        }

        g.setStroke(color);
        g.setLineWidth(width);

        Pt p0 = rp.transform.apply(pts.get(0));
        double sx0 = worldToScreenX(p0.getX());
        double sy0 = worldToScreenY(p0.getY());

        for (int i = 1; i < pts.size(); i++) {
            Pt p1 = rp.transform.apply(pts.get(i));
            double sx1 = worldToScreenX(p1.getX());
            double sy1 = worldToScreenY(p1.getY());
            g.strokeLine(sx0, sy0, sx1, sy1);
            sx0 = sx1;
            sy0 = sy1;
        }
    }

    private void drawLineWorld(GraphicsContext g, double x0, double y0, double x1, double y1) {
        g.strokeLine(worldToScreenX(x0), worldToScreenY(y0), worldToScreenX(x1), worldToScreenY(y1));
    }

    private double worldToScreenX(double x) {
        return offsetX + x * scale;
    }

    private double worldToScreenY(double y) {
        return offsetY + y * scale;
    }

    private double clamp(double v, double min, double max) {
        if (v < min) {
            return min;
        }
        if (v > max) {
            return max;
        }
        return v;
    }

    private Color colorForIndex(int i) {
        int idx = i % 6;
        if (idx == 0) {
            return Color.web("#1f77b4");
        }
        if (idx == 1) {
            return Color.web("#ff7f0e");
        }
        if (idx == 2) {
            return Color.web("#2ca02c");
        }
        if (idx == 3) {
            return Color.web("#d62728");
        }
        if (idx == 4) {
            return Color.web("#9467bd");
        }
        return Color.web("#8c564b");
    }

    /**
     * Pick point from curve by extreme X (minX if left=true, else maxX). Robust
     * even if points are not ordered.
     */
    private Pt extremeByX(Curve2D c, boolean left) {
        if (c == null) {
            return null;
        }
        List<Pt> pts = c.getPoints();
        if (pts == null || pts.isEmpty()) {
            return null;
        }

        Pt best = null;
        double bestX = left ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY;

        for (int i = 0; i < pts.size(); i++) {
            Pt p = pts.get(i);
            if (p == null) {
                continue;
            }
            double x = p.getX();
            if (!Double.isFinite(x)) {
                continue;
            }

            if (left) {
                if (x < bestX) {
                    bestX = x;
                    best = p;
                }
            } else {
                if (x > bestX) {
                    bestX = x;
                    best = p;
                }
            }
        }
        return best;
    }

    private void fitToContent() {
        if (rendered == null || rendered.isEmpty()) {
            return;
        }

        double w = canvas.getWidth();
        double h = canvas.getHeight();
        if (w <= 10.0 || h <= 10.0) {
            return;
        }

        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;

        for (int i = 0; i < rendered.size(); i++) {
            RenderedPanel rp = rendered.get(i);

            minX = Math.min(minX, minXCurve(rp, rp.panel.getTop()));
            minX = Math.min(minX, minXCurve(rp, rp.panel.getBottom()));
            minX = Math.min(minX, minXCurve(rp, rp.panel.getWaist()));
            minX = Math.min(minX, minXCurve(rp, rp.panel.getSeamToPrevUp()));
            minX = Math.min(minX, minXCurve(rp, rp.panel.getSeamToPrevDown()));
            minX = Math.min(minX, minXCurve(rp, rp.panel.getSeamToNextUp()));
            minX = Math.min(minX, minXCurve(rp, rp.panel.getSeamToNextDown()));

            minY = Math.min(minY, minYCurve(rp, rp.panel.getTop()));
            minY = Math.min(minY, minYCurve(rp, rp.panel.getBottom()));
            minY = Math.min(minY, minYCurve(rp, rp.panel.getWaist()));
            minY = Math.min(minY, minYCurve(rp, rp.panel.getSeamToPrevUp()));
            minY = Math.min(minY, minYCurve(rp, rp.panel.getSeamToPrevDown()));
            minY = Math.min(minY, minYCurve(rp, rp.panel.getSeamToNextUp()));
            minY = Math.min(minY, minYCurve(rp, rp.panel.getSeamToNextDown()));

            maxX = Math.max(maxX, maxXCurve(rp, rp.panel.getTop()));
            maxX = Math.max(maxX, maxXCurve(rp, rp.panel.getBottom()));
            maxX = Math.max(maxX, maxXCurve(rp, rp.panel.getWaist()));
            maxX = Math.max(maxXCurve(rp, rp.panel.getSeamToPrevUp()), maxX);
            maxX = Math.max(maxXCurve(rp, rp.panel.getSeamToPrevDown()), maxX);
            maxX = Math.max(maxXCurve(rp, rp.panel.getSeamToNextUp()), maxX);
            maxX = Math.max(maxXCurve(rp, rp.panel.getSeamToNextDown()), maxX);

            maxY = Math.max(maxY, maxYCurve(rp, rp.panel.getTop()));
            maxY = Math.max(maxY, maxYCurve(rp, rp.panel.getBottom()));
            maxY = Math.max(maxY, maxYCurve(rp, rp.panel.getWaist()));
            maxY = Math.max(maxY, maxYCurve(rp, rp.panel.getSeamToPrevUp()));
            maxY = Math.max(maxY, maxYCurve(rp, rp.panel.getSeamToPrevDown()));
            maxY = Math.max(maxY, maxYCurve(rp, rp.panel.getSeamToNextUp()));
            maxY = Math.max(maxY, maxYCurve(rp, rp.panel.getSeamToNextDown()));
        }

        if (!Double.isFinite(minX) || !Double.isFinite(minY) || !Double.isFinite(maxX) || !Double.isFinite(maxY)) {
            return;
        }

        double contentW = Math.max(1.0, maxX - minX);
        double contentH = Math.max(1.0, maxY - minY);

        double sx = (w * 0.90) / contentW;
        double sy = (h * 0.90) / contentH;

        scale = clamp(Math.min(sx, sy), 0.02, 200.0);

        double cx = (minX + maxX) / 2.0;
        double cy = (minY + maxY) / 2.0;

        offsetX = w / 2.0 - cx * scale;
        offsetY = h / 2.0 - cy * scale;
    }

    private double minXCurve(RenderedPanel rp, Curve2D c) {
        if (c == null || c.getPoints() == null || c.getPoints().isEmpty()) {
            return Double.POSITIVE_INFINITY;
        }
        double m = Double.POSITIVE_INFINITY;
        List<Pt> pts = c.getPoints();
        for (int i = 0; i < pts.size(); i++) {
            Pt p = rp.transform.apply(pts.get(i));
            if (p != null && Double.isFinite(p.getX())) {
                m = Math.min(m, p.getX());
            }
        }
        return m;
    }

    private double minYCurve(RenderedPanel rp, Curve2D c) {
        if (c == null || c.getPoints() == null || c.getPoints().isEmpty()) {
            return Double.POSITIVE_INFINITY;
        }
        double m = Double.POSITIVE_INFINITY;
        List<Pt> pts = c.getPoints();
        for (int i = 0; i < pts.size(); i++) {
            Pt p = rp.transform.apply(pts.get(i));
            if (p != null && Double.isFinite(p.getY())) {
                m = Math.min(m, p.getY());
            }
        }
        return m;
    }

    private double maxXCurve(RenderedPanel rp, Curve2D c) {
        if (c == null || c.getPoints() == null || c.getPoints().isEmpty()) {
            return Double.NEGATIVE_INFINITY;
        }
        double m = Double.NEGATIVE_INFINITY;
        List<Pt> pts = c.getPoints();
        for (int i = 0; i < pts.size(); i++) {
            Pt p = rp.transform.apply(pts.get(i));
            if (p != null && Double.isFinite(p.getX())) {
                m = Math.max(m, p.getX());
            }
        }
        return m;
    }

    private double maxYCurve(RenderedPanel rp, Curve2D c) {
        if (c == null || c.getPoints() == null || c.getPoints().isEmpty()) {
            return Double.NEGATIVE_INFINITY;
        }
        double m = Double.NEGATIVE_INFINITY;
        List<Pt> pts = c.getPoints();
        for (int i = 0; i < pts.size(); i++) {
            Pt p = rp.transform.apply(pts.get(i));
            if (p != null && Double.isFinite(p.getY())) {
                m = Math.max(m, p.getY());
            }
        }
        return m;
    }

    // ----------------- Measurements -----------------
    private void updateMeasurements() {
        dyLabel.setText(String.format("dyMm: %.1f mm", dyMm));

        // TODO: If circumference becomes 0 for dy<0, fix MeasurementUtils intersection to prefer Down fallback Up.
        double fullCirc = MeasurementUtils.computeFullCircumference(panels, dyMm);
        circumferenceLabel.setText(String.format("Circumference: %.1f mm", fullCirc));

        seamTableArea.setText(buildSeamTablesText());
    }

    /**
     * Two tables: TOP: from waist upwards BOTTOM: from waist downwards
     *
     * Rows: AB, BC, CD, DE, EF Columns: Left_UP, Right_UP, Diff_UP, Left_DOWN,
     * Right_DOWN, Diff_DOWN
     *
     * Left seam = (A->B) measured on left panel as TO_NEXT Right seam = (B->A)
     * measured on right panel as TO_PREV
     */
    private String buildSeamTablesText() {
        if (panels == null || panels.isEmpty()) {
            return "No panels loaded.";
        }

        PanelId[] ids = {PanelId.A, PanelId.B, PanelId.C, PanelId.D, PanelId.E, PanelId.F};

        StringBuilder sb = new StringBuilder();

        sb.append("TOP (from waist up)\n");
        sb.append(seamHeader());
        sb.append(seamSep());
        for (int i = 0; i < ids.length - 1; i++) {
            sb.append(seamRow(ids[i], ids[i + 1], true)).append('\n');
        }

        sb.append("\nBOTTOM (from waist down)\n");
        sb.append(seamHeader());
        sb.append(seamSep());
        for (int i = 0; i < ids.length - 1; i++) {
            sb.append(seamRow(ids[i], ids[i + 1], false)).append('\n');
        }

        return sb.toString();
    }

    private String seamHeader() {
        return String.format("%-3s %10s %10s %10s %10s %10s %10s\n",
                "Seam",
                "Left_UP", "Right_UP", "Diff_UP",
                "Left_DN", "Right_DN", "Diff_DN"
        );
    }

    private String seamSep() {
        return "--------------------------------------------------------------------------------\n";
    }

    private String seamRow(PanelId leftId, PanelId rightId, boolean top) {
        String name = leftId.name() + rightId.name();

        PanelCurves left = findPanel(leftId);
        PanelCurves right = findPanel(rightId);

        if (left == null || right == null) {
            return String.format("%-3s %10s %10s %10s %10s %10s %10s",
                    name, "-", "-", "-", "-", "-", "-"
            );
        }

        SeamSplit lUp = MeasurementUtils.measureSeamSplitAtWaist(left, SeamSide.TO_NEXT, true);
        SeamSplit lDn = MeasurementUtils.measureSeamSplitAtWaist(left, SeamSide.TO_NEXT, false);

        SeamSplit rUp = MeasurementUtils.measureSeamSplitAtWaist(right, SeamSide.TO_PREV, true);
        SeamSplit rDn = MeasurementUtils.measureSeamSplitAtWaist(right, SeamSide.TO_PREV, false);

        double leftUp = top ? lUp.above : lUp.below;
        double rightUp = top ? rUp.above : rUp.below;
        double diffUp = leftUp - rightUp;

        double leftDn = top ? lDn.above : lDn.below;
        double rightDn = top ? rDn.above : rDn.below;
        double diffDn = leftDn - rightDn;

        return String.format("%-3s %10.1f %10.1f %10.1f %10.1f %10.1f %10.1f",
                name,
                leftUp, rightUp, diffUp,
                leftDn, rightDn, diffDn
        );
    }

    private PanelCurves findPanel(PanelId id) {
        if (panels == null) {
            return null;
        }
        for (PanelCurves p : panels) {
            if (p.getPanelId() == id) {
                return p;
            }
        }
        return null;
    }
}
