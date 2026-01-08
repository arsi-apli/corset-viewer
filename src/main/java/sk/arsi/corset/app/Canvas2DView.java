package sk.arsi.corset.app;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sk.arsi.corset.export.SvgExporter;
import sk.arsi.corset.measure.MeasurementUtils;
import sk.arsi.corset.measure.SeamMeasurementData;
import sk.arsi.corset.measure.SeamMeasurementService;
import sk.arsi.corset.model.Curve2D;
import sk.arsi.corset.model.PanelCurves;
import sk.arsi.corset.model.PanelId;
import sk.arsi.corset.model.Pt;
import sk.arsi.corset.resize.PanelResizer;
import sk.arsi.corset.resize.ResizeMode;
import sk.arsi.corset.svg.PathSampler;
import sk.arsi.corset.svg.SvgDocument;
import sk.arsi.corset.util.SeamAllowanceComputer;

import java.io.File;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Canvas2DView {

    private static final Logger log = LoggerFactory.getLogger(Canvas2DView.class);

    private Button btnWaist;

    private enum LayoutMode {
        TOP,
        WAIST,
        BOTTOM
    }

    /**
     * Snapshot for undo history (future feature). Stores the base geometry and
     * UI state at a point in time.
     */
    private static final class Snapshot {

        private final List<PanelCurves> panelsOriginal;
        private final ResizeMode resizeMode;
        private final double resizeDeltaMm;

        private Snapshot(List<PanelCurves> panelsOriginal, ResizeMode resizeMode, double resizeDeltaMm) {
            this.panelsOriginal = panelsOriginal;
            this.resizeMode = resizeMode;
            this.resizeDeltaMm = resizeDeltaMm;
        }
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

    // --- Measurement line rendering ---
    private static final double MEASUREMENT_LINE_EXTENT = 10000.0; // extent for horizontal measurement line
    // Minimum dyMm threshold to show measurement line. Values smaller than this are too close
    // to the waist and would clutter the display. 0.1mm is chosen as it's below the practical
    // precision of corset measurements while keeping the display clean.
    private static final double MIN_DY_FOR_MEASUREMENT_LINE = 0.1;

    // --- Default slider range when panels are empty or invalid ---
    private static final double DEFAULT_MIN_DY = -200.0;
    private static final double DEFAULT_MAX_DY = 200.0;
    private static final double FALLBACK_MIN_DY = -100.0;
    private static final double FALLBACK_MAX_DY = 100.0;

    // --- Fonts: bigger / readable ---
    private static final int FONT_LABEL = 15;
    private static final int FONT_VALUE = 18;

    // --- Sampling parameters for resize ---
    // These should match the parameters used in PatternExtractor
    private static final double RESIZE_FLATNESS_MM = 0.5;
    private static final double RESIZE_RESAMPLE_STEP_MM = 0.0;

    private final Canvas canvas;
    private final BorderPane root;
    private final HBox toolbar;
    private final VBox toolbarBottomContainer;
    private final HBox toolbarBottomRow1;
    private final HBox toolbarBottomRow2;

    // host pane for canvas so we bind to center area size (prevents huge textures)
    private final StackPane canvasHost;

    // measurement UI - now in toolbar
    private final Slider circumferenceSlider;
    private final Spinner<Double> dySpinner;
    private final Label dyLabel;
    private final Label circumferenceLabel;
    private boolean isUpdatingControls; // Flag to prevent recursive updates

    // allowance UI
    private final CheckBox showAllowancesCheckBox;
    private final Spinner<Double> allowanceSpinner;
    private double allowanceDistance; // in mm

    // Resize controls
    private final Spinner<Double> resizeDeltaSpinner;
    private final ComboBox<ResizeMode> resizeModeCombo;
    private double resizeDeltaMm;
    private ResizeMode resizeMode;

    private List<PanelCurves> panelsOriginal; // original panels before resizing
    private List<PanelCurves> panels; // effective panels after resizing
    private List<RenderedPanel> rendered;
    private MeasurementsView measurementsView;
    private List<SeamMeasurementData> cachedMeasurements;

    // Undo history for Apply/Reset workflow
    private Deque<Snapshot> undoHistory;

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

    // Export configuration
    private SvgDocument svgDocument;
    private java.nio.file.Path svgPath;
    private Spinner<Integer> notchCountSpinner;
    private Spinner<Double> notchLengthSpinner;
    private CheckBox showNotchesCheckBox;

    // Cached notches for preview
    private List<sk.arsi.corset.export.PanelNotches> cachedNotches;
    private int cachedNotchCount = -1;
    private double cachedNotchLength = -1.0;

    public Canvas2DView() {
        this.canvas = new Canvas(1200, 700);
        this.root = new BorderPane();
        this.toolbar = new HBox(8.0);
        this.toolbarBottomContainer = new VBox(4.0);
        this.toolbarBottomRow1 = new HBox(8.0);
        this.toolbarBottomRow2 = new HBox(8.0);

        this.canvasHost = new StackPane(canvas);

        this.circumferenceSlider = new Slider(-200.0, 200.0, 0.0);

        // Initialize spinner with default range, will be updated when panels are loaded
        SpinnerValueFactory<Double> spinnerFactory = new SpinnerValueFactory.DoubleSpinnerValueFactory(
                -200.0, 200.0, 0.0, 1.0);
        this.dySpinner = new Spinner<>(spinnerFactory);
        this.dySpinner.setEditable(true);
        this.dySpinner.setPrefWidth(100.0);

        this.dyLabel = new Label("dyMm: 0.0 mm");
        this.circumferenceLabel = new Label("Circumference: 0.0 mm");

        // Allowance controls
        this.showAllowancesCheckBox = new CheckBox("Show allowances");
        this.showAllowancesCheckBox.setSelected(true);
        this.showAllowancesCheckBox.setOnAction(e -> redraw());

        SpinnerValueFactory<Double> allowanceFactory = new SpinnerValueFactory.DoubleSpinnerValueFactory(
                0.0, 50.0, 10.0, 1.0);
        this.allowanceSpinner = new Spinner<>(allowanceFactory);
        this.allowanceSpinner.setEditable(true);
        this.allowanceSpinner.setPrefWidth(80.0);
        this.allowanceSpinner.valueProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                allowanceDistance = newV;
                redraw();
            }
        });
        this.allowanceDistance = 10.0; // default 10mm

        // Resize controls
        SpinnerValueFactory<Double> resizeDeltaFactory = new SpinnerValueFactory.DoubleSpinnerValueFactory(
                -300.0, 300.0, 0.0, 1.0);
        this.resizeDeltaSpinner = new Spinner<>(resizeDeltaFactory);
        this.resizeDeltaSpinner.setEditable(true);
        this.resizeDeltaSpinner.setPrefWidth(80.0);
        this.resizeDeltaSpinner.valueProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                resizeDeltaMm = newV;
                applyResize();
            }
        });
        this.resizeDeltaMm = 0.0;

        this.resizeModeCombo = new ComboBox<>();
        this.resizeModeCombo.getItems().addAll(ResizeMode.DISABLED, ResizeMode.GLOBAL, ResizeMode.TOP, ResizeMode.BOTTOM, ResizeMode.HIP, ResizeMode.HIP1, ResizeMode.RIB, ResizeMode.RIB1, ResizeMode.WAIST);
        this.resizeModeCombo.setValue(ResizeMode.DISABLED);
        this.resizeModeCombo.valueProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                resizeMode = newV;
                applyResize();
            }
        });
        this.resizeMode = ResizeMode.DISABLED;

        this.panelsOriginal = new ArrayList<PanelCurves>();
        this.panels = new ArrayList<PanelCurves>();
        this.rendered = new ArrayList<RenderedPanel>();
        this.cachedMeasurements = new ArrayList<>();
        this.undoHistory = new ArrayDeque<>();

        this.scale = 2.0;
        this.offsetX = 80.0;
        this.offsetY = 350.0;

        this.dragging = false;
        this.didInitialFit = false;
        this.isUpdatingControls = false;

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
            this.panelsOriginal = new ArrayList<PanelCurves>();
            this.panels = new ArrayList<PanelCurves>();
        } else {
            this.panelsOriginal = panels;
            // Apply resize to get effective panels
            this.panels = applyResizeToOriginals();
        }

        this.didInitialFit = false;
        rebuildLayout();
        fitToContent();

        // Recompute cached measurements when panels change
        this.cachedMeasurements = SeamMeasurementService.computeAllSeamMeasurements(this.panels);

        // Invalidate notch cache when panels change
        this.cachedNotches = null;
        this.cachedNotchCount = -1;
        this.cachedNotchLength = -1.0;

        // Update slider range based on valid measurement range
        updateSliderRange();

        redraw();
    }

    public void setSeamMeasurements(MeasurementsView measurementsView) {
        this.measurementsView = measurementsView;
        if (measurementsView != null) {
            measurementsView.setOnToleranceChanged(tolerance -> redraw());
        }
    }

    public void setSvgDocument(SvgDocument svgDocument) {
        this.svgDocument = svgDocument;
    }

    public void setSvgPath(java.nio.file.Path svgPath) {
        this.svgPath = svgPath;
    }

    /**
     * Attach spinner value change listener to synchronize with slider. This
     * method is called when the spinner is first created and whenever its value
     * factory is recreated (e.g., when slider range changes).
     */
    private void attachSpinnerListener() {
        dySpinner.valueProperty().addListener((obs, oldV, newV) -> {
            if (!isUpdatingControls && newV != null) {
                isUpdatingControls = true;
                dyMm = newV;
                circumferenceSlider.setValue(dyMm);
                updateCircumferenceMeasurement();
                redraw();
                isUpdatingControls = false;
                switchMode(LayoutMode.WAIST);
            }
        });
    }

    private void initUi() {
        // --- Toolbar ---
        Button btnTop = new Button("TOP");
        btnWaist = new Button("WAIST");
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
            if (!isUpdatingControls) {
                isUpdatingControls = true;
                dyMm = newV.doubleValue();
                dySpinner.getValueFactory().setValue(dyMm);
                updateCircumferenceMeasurement();
                redraw(); // Redraw to update the measurement line
                isUpdatingControls = false;
                switchMode(LayoutMode.WAIST);
            }
        });

        // Spinner for direct numeric input
        attachSpinnerListener();

        // Reset button to return to waist (dy=0)
        Button btnReset = new Button("Reset");
        btnReset.setOnAction(e -> {
            isUpdatingControls = true;
            dyMm = 0.0;
            circumferenceSlider.setValue(0.0);
            dySpinner.getValueFactory().setValue(0.0);
            updateCircumferenceMeasurement();
            redraw();
            isUpdatingControls = false;
            switchMode(LayoutMode.WAIST);
        });

        dyLabel.setStyle("-fx-font-size: " + FONT_VALUE + "px; -fx-font-weight: bold;");
        circumferenceLabel.setStyle("-fx-font-size: " + FONT_VALUE + "px; -fx-font-weight: bold;");

        // Allowance label
        Label allowanceLabel = new Label("Allowance (mm):");
        allowanceLabel.setStyle("-fx-font-size: " + FONT_LABEL + "px;");

        // Notch count spinner
        Label notchCountLabel = new Label("Notches:");
        notchCountSpinner = new Spinner<>(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 10, 3, 1));
        notchCountSpinner.setEditable(true);
        notchCountSpinner.setPrefWidth(70.0);
        notchCountSpinner.valueProperty().addListener((obs, oldV, newV) -> {
            if (showNotchesCheckBox != null && showNotchesCheckBox.isSelected()) {
                redraw();
            }
        });

        // Notch length spinner
        Label notchLengthLabel = new Label("Length (mm):");
        notchLengthSpinner = new Spinner<>(new SpinnerValueFactory.DoubleSpinnerValueFactory(3.0, 5.0, 4.0, 0.5));
        notchLengthSpinner.setEditable(true);
        notchLengthSpinner.setPrefWidth(70.0);
        notchLengthSpinner.valueProperty().addListener((obs, oldV, newV) -> {
            if (showNotchesCheckBox != null && showNotchesCheckBox.isSelected()) {
                redraw();
            }
        });

        // Show notches checkbox
        showNotchesCheckBox = new CheckBox("Show notches");
        showNotchesCheckBox.setSelected(true);
        showNotchesCheckBox.setOnAction(e -> redraw());

        // Combined export button
        Button btnExport = new Button("Export SVG (Allowances + Notches)");
        btnExport.setOnAction(e -> exportSvgWithAllowancesAndNotches());

        // Curves-only export button
        Button btnExportCurvesOnly = new Button("Export SVG (curves only)");
        btnExportCurvesOnly.setOnAction(e -> exportSvgCurvesOnly());

        // Apply and Reset buttons for resize workflow
        Button btnApply = new Button("Apply");
        btnApply.setOnAction(e -> applyResizeChanges());

        Button btnResetResize = new Button("Reset");
        btnResetResize.setOnAction(e -> resetToOriginalSvg());

        toolbar.getChildren().addAll(
                btnTop, btnWaist, btnBottom,
                new javafx.scene.control.Separator(javafx.geometry.Orientation.VERTICAL),
                sliderLabel, circumferenceSlider, dySpinner, btnReset, dyLabel, circumferenceLabel
        );

        // Row 1: allowances, notches, export
        toolbarBottomRow1.getChildren().addAll(
                showAllowancesCheckBox, allowanceLabel, allowanceSpinner,
                showNotchesCheckBox, notchCountLabel, notchCountSpinner,
                notchLengthLabel, notchLengthSpinner,
                btnExport
        );

        // Row 2: resize controls
        toolbarBottomRow2.getChildren().addAll(
                new Label("Resize mode:"), resizeModeCombo,
                new Label("Delta (mm):"), resizeDeltaSpinner,
                btnApply, btnResetResize, btnExportCurvesOnly
        );

        toolbarBottomContainer.getChildren().addAll(toolbarBottomRow1, toolbarBottomRow2);

        toolbar.setPadding(new Insets(8.0));
        toolbarBottomRow1.setPadding(new Insets(8.0));
        toolbarBottomRow2.setPadding(new Insets(8.0));

        root.setTop(toolbar);
        root.setBottom(toolbarBottomContainer);
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
        drawAxes(g);

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

        // Draw allowances if enabled
        if (showAllowancesCheckBox.isSelected() && allowanceDistance > 0) {
            drawAllowances(g);
        }

        // Draw notches if enabled
        if (showNotchesCheckBox != null && showNotchesCheckBox.isSelected()) {
            drawNotches(g);
        }

        // Draw horizontal measurement line in WAIST mode
        // In WAIST mode, all waists are aligned to y=0, so the measurement line is at y = -dyMm
        if (mode == LayoutMode.WAIST && Math.abs(dyMm) > MIN_DY_FOR_MEASUREMENT_LINE) {
            g.setStroke(Color.BLUE);
            g.setLineWidth(2.0);
            double measurementY = -dyMm; // In WAIST mode, waist is at y=0, so measurement is at -dyMm
            drawLineWorld(g, -MEASUREMENT_LINE_EXTENT, measurementY, MEASUREMENT_LINE_EXTENT, measurementY);
        }
    }

    /**
     * Draw seam allowance offset curves for all internal seams.
     */
    private void drawAllowances(GraphicsContext g) {
        g.setStroke(Color.GREEN);
        g.setLineWidth(1.0);

        for (RenderedPanel rp : rendered) {
            PanelCurves panel = rp.panel;

            // Draw allowances for each seam that should have one
            drawAllowanceForSeam(g, rp, panel.getSeamToPrevUp(), panel);
            drawAllowanceForSeam(g, rp, panel.getSeamToPrevDown(), panel);
            drawAllowanceForSeam(g, rp, panel.getSeamToNextUp(), panel);
            drawAllowanceForSeam(g, rp, panel.getSeamToNextDown(), panel);
        }
    }

    /**
     * Draw allowance offset curve for a single seam if it should have one.
     */
    private void drawAllowanceForSeam(GraphicsContext g, RenderedPanel rp, Curve2D seamCurve, PanelCurves panel) {
        if (seamCurve == null) {
            return;
        }

        String seamId = seamCurve.getId();
        if (!SeamAllowanceComputer.shouldGenerateAllowance(seamId)) {
            return;
        }

        // Compute offset curve in panel-local coordinates
        List<Pt> offsetPoints = SeamAllowanceComputer.computeOffsetCurve(seamCurve, panel, allowanceDistance);
        if (offsetPoints == null || offsetPoints.size() < 2) {
            return;
        }

        // Transform and draw
        for (int i = 0; i < offsetPoints.size() - 1; i++) {
            Pt p0Local = offsetPoints.get(i);
            Pt p1Local = offsetPoints.get(i + 1);

            if (p0Local == null || p1Local == null) {
                continue;
            }

            // Apply panel transform to get world coordinates
            Pt p0World = rp.transform.apply(p0Local);
            Pt p1World = rp.transform.apply(p1Local);

            if (p0World == null || p1World == null) {
                continue;
            }

            // Convert to screen coordinates and draw
            double sx0 = worldToScreenX(p0World.getX());
            double sy0 = worldToScreenY(p0World.getY());
            double sx1 = worldToScreenX(p1World.getX());
            double sy1 = worldToScreenY(p1World.getY());

            g.strokeLine(sx0, sy0, sx1, sy1);
        }
    }

    /**
     * Draw notches for all panels.
     */
    private void drawNotches(GraphicsContext g) {
        if (panels == null || panels.isEmpty()) {
            return;
        }

        int notchCount = notchCountSpinner != null ? notchCountSpinner.getValue() : 3;
        double notchLength = notchLengthSpinner != null ? notchLengthSpinner.getValue() : 4.0;

        // Use cached notches if parameters haven't changed
        if (cachedNotches == null || cachedNotchCount != notchCount
                || Math.abs(cachedNotchLength - notchLength) > 0.01) {
            // Regenerate notches when parameters change
            cachedNotches = sk.arsi.corset.export.NotchGenerator.generateAllNotches(
                    panels, notchCount, notchLength);
            cachedNotchCount = notchCount;
            cachedNotchLength = notchLength;
        }

        g.setStroke(Color.BLACK);
        g.setLineWidth(1.0);

        // Draw notches for each panel with its transform
        for (int i = 0; i < rendered.size() && i < cachedNotches.size(); i++) {
            RenderedPanel rp = rendered.get(i);
            sk.arsi.corset.export.PanelNotches panelNotches = cachedNotches.get(i);

            if (panelNotches == null || panelNotches.getNotches() == null) {
                continue;
            }

            for (sk.arsi.corset.export.Notch notch : panelNotches.getNotches()) {
                // Transform notch points from panel-local to world coordinates
                Pt startWorld = rp.transform.apply(notch.getStart());
                Pt endWorld = rp.transform.apply(notch.getEnd());

                if (startWorld == null || endWorld == null) {
                    continue;
                }

                // Convert to screen coordinates and draw
                double sx0 = worldToScreenX(startWorld.getX());
                double sy0 = worldToScreenY(startWorld.getY());
                double sx1 = worldToScreenX(endWorld.getX());
                double sy1 = worldToScreenY(endWorld.getY());

                g.strokeLine(sx0, sy0, sx1, sy1);
            }
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
        if (id == null) {
            return null;
        }
        switch (id) {
            case B:
                return PanelId.A;
            case C:
                return PanelId.B;
            case D:
                return PanelId.C;
            case E:
                return PanelId.D;
            case F:
                return PanelId.E;
            default:
                return null;
        }
    }

    private PanelId getNextPanelId(PanelId id) {
        if (id == null) {
            return null;
        }
        switch (id) {
            case A:
                return PanelId.B;
            case B:
                return PanelId.C;
            case C:
                return PanelId.D;
            case D:
                return PanelId.E;
            case E:
                return PanelId.F;
            default:
                return null;
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
    private void updateSliderRange() {
        if (panels == null || panels.isEmpty()) {
            circumferenceSlider.setMin(DEFAULT_MIN_DY);
            circumferenceSlider.setMax(DEFAULT_MAX_DY);

            // Recreate spinner factory with new range
            SpinnerValueFactory<Double> spinnerFactory = new SpinnerValueFactory.DoubleSpinnerValueFactory(
                    DEFAULT_MIN_DY, DEFAULT_MAX_DY, dyMm, 1.0);
            dySpinner.setValueFactory(spinnerFactory);
            return;
        }

        MeasurementUtils.DyRange range = MeasurementUtils.computeValidDyRange(panels);

        // Set slider range: min = -maxDownDy (negative), max = +maxUpDy (positive)
        double minValue = -range.getMaxDownDy();
        double maxValue = range.getMaxUpDy();

        // Ensure we have some range even if computation fails
        if (minValue >= maxValue) {
            minValue = FALLBACK_MIN_DY;
            maxValue = FALLBACK_MAX_DY;
        }

        circumferenceSlider.setMin(minValue);
        circumferenceSlider.setMax(maxValue);

        // Update spinner range by recreating the factory
        SpinnerValueFactory<Double> spinnerFactory = new SpinnerValueFactory.DoubleSpinnerValueFactory(
                minValue, maxValue, dyMm, 1.0);
        dySpinner.setValueFactory(spinnerFactory);

        // Re-attach listener to new factory
        attachSpinnerListener();

        // Clamp current value to new range
        if (dyMm < minValue) {
            isUpdatingControls = true;
            dyMm = minValue;
            circumferenceSlider.setValue(minValue);
            dySpinner.getValueFactory().setValue(minValue);
            isUpdatingControls = false;
        } else if (dyMm > maxValue) {
            isUpdatingControls = true;
            dyMm = maxValue;
            circumferenceSlider.setValue(maxValue);
            dySpinner.getValueFactory().setValue(maxValue);
            isUpdatingControls = false;
        }

        updateCircumferenceMeasurement();
    }

    private void drawAxes(GraphicsContext g) {
        g.setStroke(Color.LIGHTGRAY);
        g.setLineWidth(1.0);
        drawLineWorld(g, -MEASUREMENT_LINE_EXTENT, 0, MEASUREMENT_LINE_EXTENT, 0);
        drawLineWorld(g, 0, -MEASUREMENT_LINE_EXTENT, 0, MEASUREMENT_LINE_EXTENT);
    }

    private void updateCircumferenceMeasurement() {
        dyLabel.setText(String.format("dyMm: %.1f mm", dyMm));

        double fullCirc = MeasurementUtils.computeFullCircumference(panels, dyMm);
        double inchFullCirc = fullCirc * 0.0393700787d;
        circumferenceLabel.setText(String.format("Circumference: %.1f mm/%.1f inch  ", fullCirc, inchFullCirc));
    }

    /**
     * Apply resize to original panels when resize controls change.
     */
    private void applyResize() {
        if (panelsOriginal == null || panelsOriginal.isEmpty()) {
            return;
        }

        this.panels = applyResizeToOriginals();

        rebuildLayout();

        // Recompute cached measurements
        this.cachedMeasurements = SeamMeasurementService.computeAllSeamMeasurements(this.panels);

        // Invalidate notch cache
        this.cachedNotches = null;
        this.cachedNotchCount = -1;
        this.cachedNotchLength = -1.0;

        updateSliderRange();
        redraw();
    }

    /**
     * Apply current resize mode and delta to original panels.
     */
    private List<PanelCurves> applyResizeToOriginals() {
        if (panelsOriginal == null || panelsOriginal.isEmpty()) {
            return new ArrayList<>();
        }

        PathSampler sampler = new PathSampler();
        PanelResizer resizer = new PanelResizer(sampler, RESIZE_FLATNESS_MM, RESIZE_RESAMPLE_STEP_MM);

        return resizer.resize(panelsOriginal, resizeMode, resizeDeltaMm);
    }

    /**
     * Export SVG with allowances and notches combined.
     */
    private void exportSvgWithAllowancesAndNotches() {
        if (panels == null || panels.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "No panels loaded", "Cannot export: no panels loaded.");
            return;
        }

        if (svgDocument == null) {
            showAlert(Alert.AlertType.WARNING, "No SVG document loaded",
                    "Cannot export: SVG document not available. Please load an SVG file first.");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export SVG (Allowances + Notches)");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("SVG files (*.svg)", "*.svg")
        );
        fileChooser.setInitialFileName("panels_with_allowances_and_notches.svg");

        // Set initial directory to the directory of the currently loaded SVG
        if (svgPath != null && svgPath.getParent() != null) {
            fileChooser.setInitialDirectory(svgPath.getParent().toFile());
        }

        File file = fileChooser.showSaveDialog(root.getScene().getWindow());
        if (file == null) {
            return; // User cancelled
        }

        try {
            int notchCount = notchCountSpinner.getValue();
            double notchLength = notchLengthSpinner.getValue();

            SvgExporter.exportWithAllowancesAndNotches(svgDocument, panels, file, notchCount, notchLength, allowanceDistance);
            showAlert(Alert.AlertType.INFORMATION, "Export successful",
                    "SVG exported to: " + file.getAbsolutePath());
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Export failed",
                    "Failed to export SVG: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Export SVG with curves only (preserves original SVG, updates only
     * modified d attributes).
     */
    private void exportSvgCurvesOnly() {
        if (panels == null || panels.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "No panels loaded", "Cannot export: no panels loaded.");
            return;
        }

        if (svgPath == null) {
            showAlert(Alert.AlertType.WARNING, "No SVG file loaded",
                    "Cannot export: SVG file path not available. Please load an SVG file first.");
            return;
        }

        if (svgDocument == null) {
            showAlert(Alert.AlertType.WARNING, "No SVG document loaded",
                    "Cannot export: SVG document not available. Please load an SVG file first.");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export SVG (curves only)");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("SVG files (*.svg)", "*.svg")
        );
        fileChooser.setInitialFileName("panels_curves_only.svg");

        // Set initial directory to the directory of the currently loaded SVG
        if (svgPath.getParent() != null) {
            fileChooser.setInitialDirectory(svgPath.getParent().toFile());
        }

        File file = fileChooser.showSaveDialog(root.getScene().getWindow());
        if (file == null) {
            return; // User cancelled
        }

        try {
            SvgExporter.exportCurvesOnly(svgPath, svgDocument, panels, file);
            showAlert(Alert.AlertType.INFORMATION, "Export successful",
                    "SVG exported to: " + file.getAbsolutePath());
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Export failed",
                    "Failed to export SVG: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Deep copy a list of PanelCurves, ensuring all curves and points are
     * independent.
     */
    private List<PanelCurves> deepCopyPanels(List<PanelCurves> source) {
        if (source == null) {
            return null;
        }

        List<PanelCurves> copy = new ArrayList<>();
        for (PanelCurves panel : source) {
            PanelCurves copiedPanel = new PanelCurves(
                    panel.getPanelId(),
                    deepCopyCurve(panel.getTop()),
                    deepCopyCurve(panel.getBottom()),
                    deepCopyCurve(panel.getWaist()),
                    deepCopyCurve(panel.getSeamToPrevUp()),
                    deepCopyCurve(panel.getSeamToPrevDown()),
                    deepCopyCurve(panel.getSeamToNextUp()),
                    deepCopyCurve(panel.getSeamToNextDown())
            );
            copy.add(copiedPanel);
        }
        return copy;
    }

    /**
     * Deep copy a single Curve2D.
     */
    private Curve2D deepCopyCurve(Curve2D source) {
        if (source == null) {
            return null;
        }

        // Copy points list
        List<Pt> copiedPoints = new ArrayList<>();
        for (Pt pt : source.getPoints()) {
            copiedPoints.add(new Pt(pt.getX(), pt.getY()));
        }

        // Create new Curve2D with copied data
        return new Curve2D(source.getId(), source.getD(), copiedPoints);
    }

    /**
     * Apply current resize changes: commit effective panels to base geometry.
     * This allows stacking/combining multiple resize operations.
     */
    private void applyResizeChanges() {
        if (panels == null || panels.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "No panels loaded",
                    "Cannot apply: no panels loaded.");
            return;
        }

        // Push current base to undo history
        Snapshot snapshot = new Snapshot(
                deepCopyPanels(panelsOriginal),
                resizeMode,
                resizeDeltaMm
        );
        undoHistory.push(snapshot);

        // Replace base geometry with current effective geometry
        panelsOriginal = deepCopyPanels(panels);

        // Reset resize controls to neutral
        isUpdatingControls = true;
        resizeModeCombo.setValue(ResizeMode.DISABLED);
        resizeDeltaSpinner.getValueFactory().setValue(0.0);
        resizeMode = ResizeMode.DISABLED;
        resizeDeltaMm = 0.0;
        isUpdatingControls = false;

        // Recompute effective panels (should be same as base now)
        panels = applyResizeToOriginals();

        // Refresh everything
        rebuildLayout();
        fitToContent();
        cachedMeasurements = SeamMeasurementService.computeAllSeamMeasurements(panels);
        cachedNotches = null;
        cachedNotchCount = -1;
        cachedNotchLength = -1.0;
        updateSliderRange();
        redraw();

        log.info("Applied resize changes. Undo history size: {}", undoHistory.size());
    }

    /**
     * Reset to original SVG from disk, discarding all resize changes.
     */
    private void resetToOriginalSvg() {
        if (svgPath == null) {
            showAlert(Alert.AlertType.WARNING, "No SVG loaded",
                    "Cannot reset: no SVG file loaded. Please open an SVG file first.");
            return;
        }

        try {
            // Reload SVG document
            sk.arsi.corset.svg.SvgLoader loader = new sk.arsi.corset.svg.SvgLoader();
            SvgDocument newSvgDoc = loader.load(svgPath);

            // Reload panels using same sampling parameters
            sk.arsi.corset.io.SvgPanelLoader panelLoader
                    = new sk.arsi.corset.io.SvgPanelLoader(RESIZE_FLATNESS_MM, RESIZE_RESAMPLE_STEP_MM);
            List<PanelCurves> newPanels = panelLoader.loadPanelsWithRetry(svgPath, 3, 100);

            // Update document and panels
            this.svgDocument = newSvgDoc;
            this.panelsOriginal = newPanels;

            // Clear undo history
            undoHistory.clear();

            // Reset resize UI to neutral
            isUpdatingControls = true;
            resizeModeCombo.setValue(ResizeMode.DISABLED);
            resizeDeltaSpinner.getValueFactory().setValue(0.0);
            resizeMode = ResizeMode.DISABLED;
            resizeDeltaMm = 0.0;
            isUpdatingControls = false;

            // Apply neutral resize (which just returns originals)
            panels = applyResizeToOriginals();

            // Refresh everything (same as setPanels does)
            didInitialFit = false;
            rebuildLayout();
            fitToContent();
            cachedMeasurements = SeamMeasurementService.computeAllSeamMeasurements(panels);
            cachedNotches = null;
            cachedNotchCount = -1;
            cachedNotchLength = -1.0;
            updateSliderRange();
            redraw();

            log.info("Reset to original SVG from: {}", svgPath);
            showAlert(Alert.AlertType.INFORMATION, "Reset successful",
                    "Reloaded original geometry from SVG file.");
        } catch (Exception e) {
            log.error("Failed to reset to original SVG", e);
            showAlert(Alert.AlertType.ERROR, "Reset failed",
                    "Failed to reload SVG: " + e.getMessage());
        }
    }
}
