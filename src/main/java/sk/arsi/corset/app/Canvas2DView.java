package sk.arsi.corset.app;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import sk.arsi.corset.measure.MeasurementUtils;
import sk.arsi.corset.measure.SeamMeasurementData;
import sk.arsi.corset.measure.SeamMeasurementService;
import sk.arsi.corset.model.Curve2D;
import sk.arsi.corset.model.PanelCurves;
import sk.arsi.corset.model.PanelId;
import sk.arsi.corset.model.Pt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private static final int FONT_LABEL = 15;
    private static final int FONT_VALUE = 18;

    private final Canvas canvas;
    private final BorderPane root;
    private final HBox toolbar;

    // host pane for canvas so we bind to center area size (prevents huge textures)
    private final StackPane canvasHost;

    // measurement UI - now in toolbar
    private final Slider circumferenceSlider;
    private final Label dyLabel;
    private final Label circumferenceLabel;

    private List<PanelCurves> panels;
    private List<RenderedPanel> rendered;
    private MeasurementsView measurementsView;
    private List<SeamMeasurementData> cachedMeasurements;

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

        this.circumferenceSlider = new Slider(-200.0, 200.0, 0.0);
        this.dyLabel = new Label("dyMm: 0.0 mm");
        this.circumferenceLabel = new Label("Circumference: 0.0 mm");

        this.panels = new ArrayList<PanelCurves>();
        this.rendered = new ArrayList<RenderedPanel>();
        this.cachedMeasurements = new ArrayList<>();

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
        
        // Recompute cached measurements when panels change
        this.cachedMeasurements = SeamMeasurementService.computeAllSeamMeasurements(this.panels);
        
        redraw();
    }

    public void setSeamMeasurements(MeasurementsView measurementsView) {
        this.measurementsView = measurementsView;
        if (measurementsView != null) {
            measurementsView.setOnToleranceChanged(tolerance -> redraw());
        }
    }

    private void initUi() {
        // --- Toolbar ---
        Button btnTop = new Button("TOP");
        Button btnWaist = new Button("WAIST");
        Button btnBottom = new Button("BOTTOM");

        btnTop.setOnAction(e -> switchMode(LayoutMode.TOP));
        btnWaist.setOnAction(e -> switchMode(LayoutMode.WAIST));
        btnBottom.setOnAction(e -> switchMode(LayoutMode.BOTTOM));

        // Circumference controls
        Label sliderLabel = new Label("Height from Waist (mm):");
        sliderLabel.setStyle("-fx-font-size: " + FONT_LABEL + "px;");

        circumferenceSlider.setShowTickLabels(false);
        circumferenceSlider.setShowTickMarks(true);
        circumferenceSlider.setMajorTickUnit(50.0);
        circumferenceSlider.setMinorTickCount(4);
        circumferenceSlider.setBlockIncrement(10.0);
        circumferenceSlider.setPrefWidth(200.0);

        circumferenceSlider.valueProperty().addListener((obs, oldV, newV) -> {
            dyMm = newV.doubleValue();
            updateCircumferenceMeasurement();
        });

        dyLabel.setStyle("-fx-font-size: " + FONT_VALUE + "px; -fx-font-weight: bold;");
        circumferenceLabel.setStyle("-fx-font-size: " + FONT_VALUE + "px; -fx-font-weight: bold;");

        toolbar.getChildren().addAll(
                btnTop, btnWaist, btnBottom,
                new javafx.scene.control.Separator(javafx.geometry.Orientation.VERTICAL),
                sliderLabel, circumferenceSlider, dyLabel, circumferenceLabel
        );
        toolbar.setPadding(new Insets(8.0));
        root.setTop(toolbar);

        // --- Center ---
        root.setCenter(canvasHost);
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

        // Compute seam highlighting map
        Map<String, SeamHighlight> highlightMap = computeSeamHighlights();

        // Unified panel color: black or very dark gray
        Color panelColor = Color.web("#222222");

        for (int i = 0; i < rendered.size(); i++) {
            RenderedPanel rp = rendered.get(i);
            PanelId panelId = rp.panel.getPanelId();

            // Draw seams with highlighting if needed
            drawSeamWithHighlight(g, rp, panelId, true, true, highlightMap);   // seamToPrevUp
            drawSeamWithHighlight(g, rp, panelId, true, false, highlightMap);  // seamToPrevDown
            drawSeamWithHighlight(g, rp, panelId, false, true, highlightMap);  // seamToNextUp
            drawSeamWithHighlight(g, rp, panelId, false, false, highlightMap); // seamToNextDown

            // top/bottom edges - unified black color
            strokeCurve(g, rp, rp.panel.getTop(), panelColor, 2.0);
            strokeCurve(g, rp, rp.panel.getBottom(), panelColor, 2.0);

            // waist - thicker black line to distinguish
            strokeCurve(g, rp, rp.panel.getWaist(), Color.BLACK, 3.0);
        }
    }

    private static class SeamHighlight {
        final boolean highlightUpTop;
        final boolean highlightUpBottom;
        final boolean highlightDownTop;
        final boolean highlightDownBottom;

        SeamHighlight(boolean highlightUpTop, boolean highlightUpBottom, 
                      boolean highlightDownTop, boolean highlightDownBottom) {
            this.highlightUpTop = highlightUpTop;
            this.highlightUpBottom = highlightUpBottom;
            this.highlightDownTop = highlightDownTop;
            this.highlightDownBottom = highlightDownBottom;
        }
        
        static SeamHighlight none() {
            return new SeamHighlight(false, false, false, false);
        }
    }

    private Map<String, SeamHighlight> computeSeamHighlights() {
        Map<String, SeamHighlight> map = new HashMap<>();
        
        if (measurementsView == null || cachedMeasurements == null) {
            return map;
        }

        double tolerance = measurementsView.getTolerance();

        for (SeamMeasurementData data : cachedMeasurements) {
            // Check which curves and portions exceed tolerance
            boolean upTopExceeds = Math.abs(data.getDiffUpTop()) > tolerance;
            boolean upBottomExceeds = Math.abs(data.getDiffUpBottom()) > tolerance;
            boolean downTopExceeds = Math.abs(data.getDiffDownTop()) > tolerance;
            boolean downBottomExceeds = Math.abs(data.getDiffDownBottom()) > tolerance;
            
            // For seam pair AB:
            // - Left panel A uses TO_NEXT (A->B): seamToNextUp and seamToNextDown
            // - Right panel B uses TO_PREV (B->A): seamToPrevUp and seamToPrevDown
            // Both should be highlighted for the same curve type (UP or DOWN) and portion (TOP or BOTTOM)
            
            // Note: AA and FF are outer seams (edges of the half-corset)
            // They are never part of measurement pairs and will be excluded from highlighting
            // via null neighborId check in drawSeamWithHighlight
            
            String leftToRight = data.getLeftPanel().name() + "->" + data.getRightPanel().name();
            String rightToLeft = data.getRightPanel().name() + "->" + data.getLeftPanel().name();
            
            SeamHighlight highlight = new SeamHighlight(upTopExceeds, upBottomExceeds, 
                                                        downTopExceeds, downBottomExceeds);
            map.put(leftToRight, highlight);
            map.put(rightToLeft, highlight);
        }

        return map;
    }

    private void drawSeamWithHighlight(
            GraphicsContext g,
            RenderedPanel rp,
            PanelId panelId,
            boolean isPrev,
            boolean isUp,
            Map<String, SeamHighlight> highlightMap) {
        
        Curve2D curve;
        PanelId neighborId;
        
        if (isPrev) {
            curve = isUp ? rp.panel.getSeamToPrevUp() : rp.panel.getSeamToPrevDown();
            neighborId = getPrevPanelId(panelId);
        } else {
            curve = isUp ? rp.panel.getSeamToNextUp() : rp.panel.getSeamToNextDown();
            neighborId = getNextPanelId(panelId);
        }

        // Default color: black
        Color seamColor = Color.web("#222222");
        
        // If curve is null, nothing to draw
        if (curve == null) {
            return;
        }
        
        // If neighborId is null, this is an outer seam (AA or FF) - always draw in black
        if (neighborId == null) {
            strokeCurve(g, rp, curve, seamColor, 1.5);
            return;
        }

        String seamKey = panelId.name() + "->" + neighborId.name();
        SeamHighlight highlight = highlightMap.get(seamKey);
        
        if (highlight != null) {
            // Determine which portions to highlight based on curve type (UP or DOWN)
            boolean highlightTop;
            boolean highlightBottom;
            
            if (isUp) {
                highlightTop = highlight.highlightUpTop;
                highlightBottom = highlight.highlightUpBottom;
            } else {
                highlightTop = highlight.highlightDownTop;
                highlightBottom = highlight.highlightDownBottom;
            }
            
            if (highlightTop || highlightBottom) {
                // Split curve at waist and draw with appropriate colors
                double waistY = MeasurementUtils.computePanelWaistY0(rp.panel.getWaist());
                strokeCurveSplit(g, rp, curve, waistY, 
                        highlightTop ? Color.RED : seamColor,
                        highlightBottom ? Color.RED : seamColor,
                        1.5);
                return;
            }
        }

        // No highlighting needed
        strokeCurve(g, rp, curve, seamColor, 1.5);
    }

    private void strokeCurveSplit(
            GraphicsContext g,
            RenderedPanel rp,
            Curve2D curve,
            double waistY,
            Color aboveColor,
            Color belowColor,
            double width) {
        
        if (curve == null) {
            return;
        }
        List<Pt> pts = curve.getPoints();
        if (pts == null || pts.size() < 2) {
            return;
        }

        g.setLineWidth(width);

        for (int i = 0; i < pts.size() - 1; i++) {
            // Get original points in panel-local coordinates
            Pt localP0 = pts.get(i);
            Pt localP1 = pts.get(i + 1);
            
            if (localP0 == null || localP1 == null) {
                continue;
            }
            
            // Get Y coordinates in panel-local coordinates for comparison with waistY
            double localY0 = localP0.getY();
            double localY1 = localP1.getY();
            
            // Determine if points are above or below waist in panel-local coordinates
            boolean p0Above = localY0 < waistY;
            boolean p1Above = localY1 < waistY;
            
            // Transform points to world coordinates for rendering
            Pt p0 = rp.transform.apply(localP0);
            Pt p1 = rp.transform.apply(localP1);
            
            if (p0 == null || p1 == null) {
                continue;
            }

            double sx0 = worldToScreenX(p0.getX());
            double sy0 = worldToScreenY(p0.getY());
            double sx1 = worldToScreenX(p1.getX());
            double sy1 = worldToScreenY(p1.getY());

            if (p0Above == p1Above) {
                // Segment is entirely above or below waist
                Color color = p0Above ? aboveColor : belowColor;
                g.setStroke(color);
                g.strokeLine(sx0, sy0, sx1, sy1);
            } else {
                // Segment crosses waist - split it in panel-local coordinates
                double t = (waistY - localY0) / (localY1 - localY0);
                double localXSplit = localP0.getX() + t * (localP1.getX() - localP0.getX());
                
                // Transform the split point to world coordinates
                Pt localSplit = new Pt(localXSplit, waistY);
                Pt worldSplit = rp.transform.apply(localSplit);
                
                if (worldSplit == null) {
                    continue;
                }
                
                double sxSplit = worldToScreenX(worldSplit.getX());
                double sySplit = worldToScreenY(worldSplit.getY());

                if (p0Above) {
                    g.setStroke(aboveColor);
                    g.strokeLine(sx0, sy0, sxSplit, sySplit);
                    g.setStroke(belowColor);
                    g.strokeLine(sxSplit, sySplit, sx1, sy1);
                } else {
                    g.setStroke(belowColor);
                    g.strokeLine(sx0, sy0, sxSplit, sySplit);
                    g.setStroke(aboveColor);
                    g.strokeLine(sxSplit, sySplit, sx1, sy1);
                }
            }
        }
    }

    private PanelId getPrevPanelId(PanelId id) {
        if (id == null) return null;
        switch (id) {
            case B: return PanelId.A;
            case C: return PanelId.B;
            case D: return PanelId.C;
            case E: return PanelId.D;
            case F: return PanelId.E;
            default: return null;
        }
    }

    private PanelId getNextPanelId(PanelId id) {
        if (id == null) return null;
        switch (id) {
            case A: return PanelId.B;
            case B: return PanelId.C;
            case C: return PanelId.D;
            case D: return PanelId.E;
            case E: return PanelId.F;
            default: return null;
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
    private void updateCircumferenceMeasurement() {
        dyLabel.setText(String.format("dyMm: %.1f mm", dyMm));

        double fullCirc = MeasurementUtils.computeFullCircumference(panels, dyMm);
        circumferenceLabel.setText(String.format("Circumference: %.1f mm", fullCirc));
    }
}
