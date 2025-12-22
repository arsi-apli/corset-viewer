package sk.arsi.corset.app;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import sk.arsi.corset.layout.ChainLayoutEngine;
import sk.arsi.corset.layout.ChainLayoutEngine.EdgeMode;
import sk.arsi.corset.layout.ChainLayoutEngine.LayoutResult;
import sk.arsi.corset.layout.ChainLayoutEngine.Transform2D;
import sk.arsi.corset.layout.PanelOrderDetector;
import sk.arsi.corset.model.Curve2D;
import sk.arsi.corset.model.PanelCurves;
import sk.arsi.corset.model.Pt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Pseudo 3D view that chains corset panels along TOP or BOTTOM edge.
 */
public final class Pseudo3DView {

    private final Canvas canvas;
    private final BorderPane root;
    private final HBox toolbar;

    private List<PanelCurves> panels;
    private List<LayoutResult> layoutResults;
    private EdgeMode edgeMode;
    private boolean orderAtoF;

    // View transform (world -> screen)
    private double scale;
    private double offsetX;
    private double offsetY;

    private double lastMouseX;
    private double lastMouseY;
    private boolean dragging;

    private boolean didInitialFit;

    private final ChainLayoutEngine layoutEngine;
    private final PanelOrderDetector orderDetector;

    public Pseudo3DView() {
        this.canvas = new Canvas(1200, 700);
        this.root = new BorderPane();
        this.toolbar = new HBox(8.0);

        this.panels = new ArrayList<>();
        this.layoutResults = new ArrayList<>();
        this.edgeMode = EdgeMode.TOP;
        this.orderAtoF = true;

        this.scale = 2.0;
        this.offsetX = 80.0;
        this.offsetY = 350.0;

        this.dragging = false;
        this.didInitialFit = false;

        this.layoutEngine = new ChainLayoutEngine();
        this.orderDetector = new PanelOrderDetector();

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
            this.panels = new ArrayList<>();
        } else {
            this.panels = panels;
        }

        // Auto-detect panel order
        this.orderAtoF = orderDetector.detectOrderAtoF(this.panels);

        this.didInitialFit = false;
        rebuildLayout();
        fitToContent();
        redraw();
    }

    private void initUi() {
        Button btnTop = new Button("TOP");
        Button btnBottom = new Button("BOTTOM");

        btnTop.setOnAction(e -> {
            edgeMode = EdgeMode.TOP;
            didInitialFit = false;
            rebuildLayout();
            fitToContent();
            redraw();
            root.requestFocus();
        });

        btnBottom.setOnAction(e -> {
            edgeMode = EdgeMode.BOTTOM;
            didInitialFit = false;
            rebuildLayout();
            fitToContent();
            redraw();
            root.requestFocus();
        });

        toolbar.getChildren().addAll(btnTop, btnBottom);
        toolbar.setPadding(new Insets(8.0));
        root.setTop(toolbar);
        root.setCenter(canvas);
    }

    private void bindResize() {
        canvas.widthProperty().bind(root.widthProperty());
        canvas.heightProperty().bind(root.heightProperty().subtract(toolbar.heightProperty()));

        root.widthProperty().addListener((obs, oldV, newV) -> {
            if (!didInitialFit) {
                fitToContent();
                didInitialFit = true;
            }
            redraw();
        });

        root.heightProperty().addListener((obs, oldV, newV) -> {
            if (!didInitialFit) {
                fitToContent();
                didInitialFit = true;
            }
            redraw();
        });
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

            offsetX += dx;
            offsetY += dy;

            lastMouseX = e.getX();
            lastMouseY = e.getY();

            redraw();
        });

        root.addEventFilter(ScrollEvent.SCROLL, e -> {
            double factor = e.getDeltaY() > 0 ? 1.1 : 1.0 / 1.1;

            double mx = e.getX();
            double my = e.getY();

            double worldXBefore = screenToWorldX(mx);
            double worldYBefore = screenToWorldY(my);

            scale = clamp(scale * factor, 0.02, 200.0);

            double worldXAfter = screenToWorldX(mx);
            double worldYAfter = screenToWorldY(my);

            offsetX = offsetX + (worldXBefore - worldXAfter) * scale;
            offsetY = offsetY + (worldYBefore - worldYAfter) * scale;

            redraw();
            e.consume();
        });

        root.setOnKeyPressed(e -> {
            KeyCode code = e.getCode();
            if (code == KeyCode.F) {
                fitToContent();
                redraw();
            } else if (code == KeyCode.DIGIT1) {
                edgeMode = EdgeMode.TOP;
                didInitialFit = false;
                rebuildLayout();
                fitToContent();
                redraw();
            } else if (code == KeyCode.DIGIT2) {
                edgeMode = EdgeMode.BOTTOM;
                didInitialFit = false;
                rebuildLayout();
                fitToContent();
                redraw();
            }
        });
    }

    private void rebuildLayout() {
        if (panels == null || panels.isEmpty()) {
            layoutResults = new ArrayList<>();
            return;
        }

        // Order panels based on detected order
        List<PanelCurves> orderedPanels = orderAtoF ? panels : reverseList(panels);

        // Compute layout using chain engine
        layoutResults = layoutEngine.computeLayout(orderedPanels, edgeMode);
    }

    private List<PanelCurves> reverseList(List<PanelCurves> list) {
        List<PanelCurves> reversed = new ArrayList<>(list);
        Collections.reverse(reversed);
        return reversed;
    }

    private void redraw() {
        GraphicsContext g = canvas.getGraphicsContext2D();

        double w = canvas.getWidth();
        double h = canvas.getHeight();

        // Light background
        g.setFill(Color.web("#f5f5f5"));
        g.fillRect(0, 0, w, h);

        // Mode label
        g.setFill(Color.GRAY);
        String orderLabel = orderAtoF ? "A→F" : "F→A";
        g.fillText("Pseudo 3D Mode: " + edgeMode + " (Order: " + orderLabel + ")", 12, 16);

        // Draw panels
        for (int i = 0; i < layoutResults.size(); i++) {
            LayoutResult result = layoutResults.get(i);
            PanelCurves panel = result.getPanel();
            Transform2D transform = result.getTransform();

            Color baseColor = colorForIndex(i);

            // Draw seams (darker)
            strokeCurve(g, transform, panel.getSeamToPrevUp(), baseColor.darker(), 1.5);
            strokeCurve(g, transform, panel.getSeamToPrevDown(), baseColor.darker(), 1.5);
            strokeCurve(g, transform, panel.getSeamToNextUp(), baseColor.darker(), 1.5);
            strokeCurve(g, transform, panel.getSeamToNextDown(), baseColor.darker(), 1.5);

            // Draw waist (black)
            strokeCurve(g, transform, panel.getWaist(), Color.BLACK, 3.0);

            // Draw top/bottom with highlighting
            if (edgeMode == EdgeMode.TOP) {
                strokeCurve(g, transform, panel.getTop(), Color.RED, 2.5);
                strokeCurve(g, transform, panel.getBottom(), baseColor, 2.0);
            } else {
                strokeCurve(g, transform, panel.getTop(), baseColor, 2.0);
                strokeCurve(g, transform, panel.getBottom(), Color.BLUE, 2.5);
            }
        }
    }

    private void strokeCurve(GraphicsContext g, Transform2D transform, Curve2D curve, Color color, double width) {
        if (curve == null) {
            return;
        }

        List<Pt> pts = curve.getPoints();
        if (pts == null || pts.size() < 2) {
            return;
        }

        g.setStroke(color);
        g.setLineWidth(width);

        Pt p0 = transform.apply(pts.get(0));
        if (p0 == null) {
            return;
        }

        double sx0 = worldToScreenX(p0.getX());
        double sy0 = worldToScreenY(p0.getY());

        for (int i = 1; i < pts.size(); i++) {
            Pt p1 = transform.apply(pts.get(i));
            if (p1 == null) {
                continue;
            }
            
            double sx1 = worldToScreenX(p1.getX());
            double sy1 = worldToScreenY(p1.getY());
            g.strokeLine(sx0, sy0, sx1, sy1);
            sx0 = sx1;
            sy0 = sy1;
        }
    }

    private double worldToScreenX(double x) {
        return offsetX + x * scale;
    }

    private double worldToScreenY(double y) {
        return offsetY + y * scale;
    }

    private double screenToWorldX(double sx) {
        return (sx - offsetX) / scale;
    }

    private double screenToWorldY(double sy) {
        return (sy - offsetY) / scale;
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

    private void fitToContent() {
        if (layoutResults == null || layoutResults.isEmpty()) {
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

        for (LayoutResult result : layoutResults) {
            PanelCurves panel = result.getPanel();
            Transform2D transform = result.getTransform();

            minX = Math.min(minX, minXCurve(transform, panel.getTop()));
            minX = Math.min(minX, minXCurve(transform, panel.getBottom()));
            minX = Math.min(minX, minXCurve(transform, panel.getWaist()));
            minX = Math.min(minX, minXCurve(transform, panel.getSeamToPrevUp()));
            minX = Math.min(minX, minXCurve(transform, panel.getSeamToPrevDown()));
            minX = Math.min(minX, minXCurve(transform, panel.getSeamToNextUp()));
            minX = Math.min(minX, minXCurve(transform, panel.getSeamToNextDown()));

            minY = Math.min(minY, minYCurve(transform, panel.getTop()));
            minY = Math.min(minY, minYCurve(transform, panel.getBottom()));
            minY = Math.min(minY, minYCurve(transform, panel.getWaist()));
            minY = Math.min(minY, minYCurve(transform, panel.getSeamToPrevUp()));
            minY = Math.min(minY, minYCurve(transform, panel.getSeamToPrevDown()));
            minY = Math.min(minY, minYCurve(transform, panel.getSeamToNextUp()));
            minY = Math.min(minY, minYCurve(transform, panel.getSeamToNextDown()));

            maxX = Math.max(maxX, maxXCurve(transform, panel.getTop()));
            maxX = Math.max(maxX, maxXCurve(transform, panel.getBottom()));
            maxX = Math.max(maxX, maxXCurve(transform, panel.getWaist()));
            maxX = Math.max(maxX, maxXCurve(transform, panel.getSeamToPrevUp()));
            maxX = Math.max(maxX, maxXCurve(transform, panel.getSeamToPrevDown()));
            maxX = Math.max(maxX, maxXCurve(transform, panel.getSeamToNextUp()));
            maxX = Math.max(maxX, maxXCurve(transform, panel.getSeamToNextDown()));

            maxY = Math.max(maxY, maxYCurve(transform, panel.getTop()));
            maxY = Math.max(maxY, maxYCurve(transform, panel.getBottom()));
            maxY = Math.max(maxY, maxYCurve(transform, panel.getWaist()));
            maxY = Math.max(maxY, maxYCurve(transform, panel.getSeamToPrevUp()));
            maxY = Math.max(maxY, maxYCurve(transform, panel.getSeamToPrevDown()));
            maxY = Math.max(maxY, maxYCurve(transform, panel.getSeamToNextUp()));
            maxY = Math.max(maxY, maxYCurve(transform, panel.getSeamToNextDown()));
        }

        if (!Double.isFinite(minX) || !Double.isFinite(minY)
                || !Double.isFinite(maxX) || !Double.isFinite(maxY)) {
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

    private double minXCurve(Transform2D transform, Curve2D c) {
        if (c == null || c.getPoints() == null || c.getPoints().isEmpty()) {
            return Double.POSITIVE_INFINITY;
        }

        double m = Double.POSITIVE_INFINITY;
        List<Pt> pts = c.getPoints();
        for (Pt p : pts) {
            Pt tp = transform.apply(p);
            if (tp != null && Double.isFinite(tp.getX())) {
                m = Math.min(m, tp.getX());
            }
        }
        return m;
    }

    private double minYCurve(Transform2D transform, Curve2D c) {
        if (c == null || c.getPoints() == null || c.getPoints().isEmpty()) {
            return Double.POSITIVE_INFINITY;
        }

        double m = Double.POSITIVE_INFINITY;
        List<Pt> pts = c.getPoints();
        for (Pt p : pts) {
            Pt tp = transform.apply(p);
            if (tp != null && Double.isFinite(tp.getY())) {
                m = Math.min(m, tp.getY());
            }
        }
        return m;
    }

    private double maxXCurve(Transform2D transform, Curve2D c) {
        if (c == null || c.getPoints() == null || c.getPoints().isEmpty()) {
            return Double.NEGATIVE_INFINITY;
        }

        double m = Double.NEGATIVE_INFINITY;
        List<Pt> pts = c.getPoints();
        for (Pt p : pts) {
            Pt tp = transform.apply(p);
            if (tp != null && Double.isFinite(tp.getX())) {
                m = Math.max(m, tp.getX());
            }
        }
        return m;
    }

    private double maxYCurve(Transform2D transform, Curve2D c) {
        if (c == null || c.getPoints() == null || c.getPoints().isEmpty()) {
            return Double.NEGATIVE_INFINITY;
        }

        double m = Double.NEGATIVE_INFINITY;
        List<Pt> pts = c.getPoints();
        for (Pt p : pts) {
            Pt tp = transform.apply(p);
            if (tp != null && Double.isFinite(tp.getY())) {
                m = Math.max(m, tp.getY());
            }
        }
        return m;
    }
}
