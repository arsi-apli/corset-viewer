package sk.arsi.corset.app;

import javafx.geometry.Point3D;
import javafx.scene.AmbientLight;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.PerspectiveCamera;
import javafx.scene.SceneAntialiasing;
import javafx.scene.SubScene;
import javafx.scene.input.MouseButton;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Cylinder;
import javafx.scene.shape.MeshView;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Translate;
import sk.arsi.corset.model.PanelCurves;
import sk.arsi.corset.render3d.RingLoftMeshBuilder;

import java.util.List;
import sk.arsi.corset.geom.SeamUtils;
import sk.arsi.corset.geom.SeamUtils.Pt2;
import sk.arsi.corset.geom.SeamUtils.SeamPolyline;
import sk.arsi.corset.render3d.PanelTextureFactory;

public final class Corset3DView {

    private final BorderPane root;

    private final Group world;
    private final Group modelGroup;
    private final Group wireGroup;

    private final SubScene subScene;
    private final PerspectiveCamera camera;

    private final Rotate rotX;
    private final Rotate rotY;
    private final Translate camTranslate;

    private double lastX;
    private double lastY;
    private boolean dragging;

    public Corset3DView() {
        this.root = new BorderPane();

        this.world = new Group();
        this.modelGroup = new Group();
        this.wireGroup = new Group();

        this.rotX = new Rotate(-20.0, Rotate.X_AXIS);
        this.rotY = new Rotate(-30.0, Rotate.Y_AXIS);
        this.modelGroup.getTransforms().addAll(rotY, rotX);
        this.wireGroup.getTransforms().addAll(rotY, rotX);

        this.world.getChildren().add(modelGroup);
        this.world.getChildren().add(wireGroup);

        AmbientLight amb = new AmbientLight(Color.color(0.6, 0.6, 0.6));
        javafx.scene.PointLight light = new javafx.scene.PointLight(Color.WHITE);
        light.getTransforms().addAll(new Translate(-500, -500, -800));
        this.world.getChildren().addAll(amb, light);

        this.subScene = new SubScene(world, 900, 700, true, SceneAntialiasing.BALANCED);
        this.subScene.setFill(Color.web("#f7f7f7"));

        this.camera = new PerspectiveCamera(true);
        this.camTranslate = new Translate(0, 0, -1200);
        this.camera.getTransforms().add(camTranslate);
        this.camera.setNearClip(0.1);
        this.camera.setFarClip(100000.0);
        this.subScene.setCamera(camera);

        this.root.setCenter(subScene);

        bindResize();
        bindInput();
    }

    public Node getNode() {
        return root;
    }

    public void setPanels(List<PanelCurves> panels) {
        modelGroup.getChildren().clear();
        wireGroup.getChildren().clear();

        if (panels == null || panels.isEmpty()) {
            return;
        }

        double[] offsets = buildSafeOffsetsMm(panels, 11, 5.0);
        RingLoftMeshBuilder.BuildConfig cfg
                = new RingLoftMeshBuilder.BuildConfig(offsets, -Math.PI / 2.0, 1.0, 12);

        List<MeshView> meshes = RingLoftMeshBuilder.buildHalfCorsetMeshesTextured(
                panels,
                cfg,
                p -> PanelTextureFactory.buildPanelTexture(p, cfg, 1024)
        );
        for (int i = 0; i < meshes.size(); i++) {
            modelGroup.getChildren().add(meshes.get(i));
        }

        // Outline curves (TOP / WAIST / BOTTOM)
        List<List<Point3D>> rings = RingLoftMeshBuilder.buildHalfCorsetRingPolylines(panels, cfg);
        int waistIdx = findClosestToZero(offsets);
        addWirePolylineSafe(rings, waistIdx, 3, Color.DARKGRAY);

// NEW: real top/bottom edge outlines (curvy, nie kruh)
        List<List<Point3D>> topOut = RingLoftMeshBuilder.buildEdgeOutlinesPerPanel(panels, cfg, true, 120);
        List<List<Point3D>> botOut = RingLoftMeshBuilder.buildEdgeOutlinesPerPanel(panels, cfg, false, 120);

        for (int i = 0; i < topOut.size(); i++) {
            List<Point3D> poly = topOut.get(i);
            if (poly != null && poly.size() >= 2) {
                wireGroup.getChildren().add(build3DPolyline(poly, 3, Color.BLACK));
            }
        }
        for (int i = 0; i < botOut.size(); i++) {
            List<Point3D> poly = botOut.get(i);
            if (poly != null && poly.size() >= 2) {
                wireGroup.getChildren().add(build3DPolyline(poly, 3, Color.BLACK));
            }
        }

    }

    private double[] buildSafeOffsetsMm(List<PanelCurves> panels, int stepsOdd, double marginMm) {
        // stepsOdd napr 11 => symetricky okolo 0
        if (stepsOdd < 3 || (stepsOdd % 2) == 0) {
            throw new IllegalArgumentException("stepsOdd must be odd and >= 3");
        }

        double minUp = Double.POSITIVE_INFINITY;    // koľko mm vieme ísť "hore" (offset +)
        double minDown = Double.POSITIVE_INFINITY;  // koľko mm vieme ísť "dole" (offset -)

        for (int i = 0; i < panels.size(); i++) {
            PanelCurves p = panels.get(i);
            double waistY = estimateWaistYLocal(p.getWaist());

            SeamPolyline left = SeamUtils.buildSeamPolyline(
                    toPtsLocal(p.getSeamToPrevUp()),
                    toPtsLocal(p.getSeamToPrevDown()),
                    waistY
            );
            SeamPolyline right = SeamUtils.buildSeamPolyline(
                    toPtsLocal(p.getSeamToNextUp()),
                    toPtsLocal(p.getSeamToNextDown()),
                    waistY
            );

            double upL = maxOffsetUp(left, waistY);
            double upR = maxOffsetUp(right, waistY);
            double downL = maxOffsetDown(left, waistY);
            double downR = maxOffsetDown(right, waistY);

            // aby ring existoval, musia existovať OBE švy panelu
            double upPanel = Math.min(upL, upR);
            double downPanel = Math.min(downL, downR);

            minUp = Math.min(minUp, upPanel);
            minDown = Math.min(minDown, downPanel);
        }

        if (!Double.isFinite(minUp) || !Double.isFinite(minDown)) {
            // fallback
            return new double[]{-80, -40, 0, 40, 80};
        }

        // pridaj bezpečnostnú rezervu, aby TOP/BOTTOM ring nebol presne na konci (tam často zlyhá intersect kvôli horizontálnym segmentom)
        double up = Math.max(5.0, minUp - marginMm);
        double down = Math.max(5.0, minDown - marginMm);

        int mid = stepsOdd / 2;
        double[] offsets = new double[stepsOdd];

        for (int s = 0; s < stepsOdd; s++) {
            double t = (double) (s - mid) / (double) mid; // -1..+1
            // t<0 => bottom, t>0 => top
            offsets[s] = (t < 0.0) ? (t * down) : (t * up);
        }
        return offsets;
    }

    private double estimateWaistYLocal(sk.arsi.corset.model.Curve2D waist) {
        List<Pt2> pts = toPtsLocal(waist);
        Pt2 a = pts.get(0);
        Pt2 b = pts.get(pts.size() - 1);
        return 0.5 * (a.y + b.y);
    }

    private List<Pt2> toPtsLocal(sk.arsi.corset.model.Curve2D curve) {
        List<sk.arsi.corset.model.Pt> src = curve.getPoints();
        List<Pt2> out = new java.util.ArrayList<Pt2>(src.size());
        for (sk.arsi.corset.model.Pt p : src) {
            out.add(new Pt2(p.getX(), p.getY()));
        }
        return out;
    }

    private double maxOffsetUp(SeamPolyline seam, double waistY) {
        // offset>0 je smer TOP => menšie Y (SVG)
        double minY = Double.POSITIVE_INFINITY;
        for (int i = 0; i < seam.pts.size(); i++) {
            Pt2 p = seam.pts.get(i);
            minY = Math.min(minY, p.y);
        }
        return Math.max(0.0, waistY - minY);
    }

    private double maxOffsetDown(SeamPolyline seam, double waistY) {
        double maxY = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < seam.pts.size(); i++) {
            Pt2 p = seam.pts.get(i);
            maxY = Math.max(maxY, p.y);
        }
        return Math.max(0.0, maxY - waistY);
    }

    private void addWirePolylineSafe(List<List<Point3D>> rings, int idx, double radius, Color color) {
        if (rings == null) {
            return;
        }
        if (idx < 0 || idx >= rings.size()) {
            return;
        }
        List<Point3D> pts = rings.get(idx);
        if (pts == null || pts.size() < 2) {
            return;
        }
        Node n = build3DPolyline(pts, radius, color);
        wireGroup.getChildren().add(n);
    }

    private Node build3DPolyline(List<Point3D> pts, double radius, Color color) {
        Group g = new Group();
        PhongMaterial mat = new PhongMaterial(color);

        for (int i = 0; i < pts.size() - 1; i++) {
            Point3D a = pts.get(i);
            Point3D b = pts.get(i + 1);
            Cylinder seg = buildSegment(a, b, radius, mat);
            if (seg != null) {
                g.getChildren().add(seg);
            }
        }
        return g;
    }

    private Cylinder buildSegment(Point3D a, Point3D b, double radius, PhongMaterial mat) {
        Point3D d = b.subtract(a);
        double h = d.magnitude();
        if (h < 1e-6) {
            return null;
        }

        Cylinder c = new Cylinder(radius, h);
        c.setMaterial(mat);

        Point3D mid = a.midpoint(b);
        c.getTransforms().add(new Translate(mid.getX(), mid.getY(), mid.getZ()));

        // Default cylinder axis is Y. Rotate from Y-axis to vector d.
        Point3D yAxis = new Point3D(0, 1, 0);
        Point3D axis = yAxis.crossProduct(d);
        double axisLen = axis.magnitude();

        if (axisLen < 1e-9) {
            // Parallel (up/down). If pointing down, rotate 180 around X.
            if (yAxis.dotProduct(d) < 0) {
                c.getTransforms().add(new Rotate(180, Rotate.X_AXIS));
            }
            return c;
        }

        double angle = Math.toDegrees(Math.acos(yAxis.normalize().dotProduct(d.normalize())));
        c.getTransforms().add(new Rotate(angle, axis));

        return c;
    }

    private int findClosestToZero(double[] a) {
        int best = 0;
        double bestAbs = Math.abs(a[0]);
        for (int i = 1; i < a.length; i++) {
            double v = Math.abs(a[i]);
            if (v < bestAbs) {
                bestAbs = v;
                best = i;
            }
        }
        return best;
    }

    private void bindResize() {
        root.widthProperty().addListener((obs, ov, nv) -> subScene.setWidth(nv.doubleValue()));
        root.heightProperty().addListener((obs, ov, nv) -> subScene.setHeight(nv.doubleValue()));
    }

    private void bindInput() {
        dragging = false;

        subScene.setOnMousePressed(e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                dragging = true;
                lastX = e.getSceneX();
                lastY = e.getSceneY();
            }
        });

        subScene.setOnMouseReleased(e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                dragging = false;
            }
        });

        subScene.setOnMouseDragged(e -> {
            if (!dragging) {
                return;
            }
            double dx = e.getSceneX() - lastX;
            double dy = e.getSceneY() - lastY;

            rotY.setAngle(rotY.getAngle() + dx * 0.35);
            rotX.setAngle(rotX.getAngle() - dy * 0.35);

            lastX = e.getSceneX();
            lastY = e.getSceneY();
        });

        subScene.addEventFilter(ScrollEvent.SCROLL, e -> {
            double dz = e.getDeltaY() > 0 ? 80.0 : -80.0;
            camTranslate.setZ(camTranslate.getZ() + dz);
            e.consume();
        });
    }
}
