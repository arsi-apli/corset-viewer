package sk.arsi.corset.render3d;

import javafx.geometry.Point3D;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.TriangleMesh;
import sk.arsi.corset.geom.SeamUtils;
import sk.arsi.corset.geom.SeamUtils.Pt2;
import sk.arsi.corset.geom.SeamUtils.SeamPolyline;
import sk.arsi.corset.model.Curve2D;
import sk.arsi.corset.model.PanelCurves;
import sk.arsi.corset.model.Pt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class RingLoftMeshBuilder {

    public static final class BuildConfig {

        public final double[] offsetsMm;    // e.g. {-120,-80,-40,0,40,80,120}
        public final double thetaStartRad;  // e.g. -PI/2
        public final double scale;          // mm -> world units (1.0 = mm)
        public final int panelSubdiv;       // e.g. 8 (>=1)

        public BuildConfig(double[] offsetsMm, double thetaStartRad, double scale, int panelSubdiv) {
            this.offsetsMm = offsetsMm;
            this.thetaStartRad = thetaStartRad;
            this.scale = scale;
            this.panelSubdiv = panelSubdiv;
        }
    }

    private RingLoftMeshBuilder() {
    }

    public static List<MeshView> buildHalfCorsetMeshes(List<PanelCurves> panels, BuildConfig cfg) {
        if (panels == null || panels.isEmpty()) {
            return new ArrayList<MeshView>();
        }
        if (cfg.panelSubdiv < 1) {
            throw new IllegalArgumentException("panelSubdiv must be >= 1");
        }

        int panelCount = panels.size();
        int rings = cfg.offsetsMm.length;

        int waistRing = findClosestToZero(cfg.offsetsMm);

        // widths per ring/panel (mm) + validity
        double[][] wPanel = new double[rings][panelCount];
        boolean[][] valid = new boolean[rings][panelCount];

        for (int k = 0; k < rings; k++) {
            double offset = cfg.offsetsMm[k];

            for (int i = 0; i < panelCount; i++) {
                PanelCurves p = panels.get(i);

                double waistY = estimateWaistY(p.getWaist());

                SeamPolyline leftSeam = SeamUtils.buildSeamPolyline(
                        toPts(p.getSeamToPrevUp()),
                        toPts(p.getSeamToPrevDown()),
                        waistY
                );
                SeamPolyline rightSeam = SeamUtils.buildSeamPolyline(
                        toPts(p.getSeamToNextUp()),
                        toPts(p.getSeamToNextDown()),
                        waistY
                );

                double targetY = waistY - offset;

                Pt2 L = sampleAtY(leftSeam, targetY);
                Pt2 R = sampleAtY(rightSeam, targetY);

                if (L == null || R == null) {
                    valid[k][i] = false;
                    wPanel[k][i] = 0.0;
                } else {
                    valid[k][i] = true;
                    wPanel[k][i] = R.minus(L).len();
                }
            }
        }

        // half circumference per ring + radius per ring
        double[] cHalf = new double[rings];
        double[] radiusMm = new double[rings];

        for (int k = 0; k < rings; k++) {
            double sum = 0.0;
            for (int i = 0; i < panelCount; i++) {
                if (valid[k][i]) {
                    sum += wPanel[k][i];
                }
            }
            cHalf[k] = sum;
        }

        double cHalfWaist = cHalf[waistRing];
        if (cHalfWaist <= 1e-9) {
            throw new IllegalStateException("Waist half-circumference is zero.");
        }
        double waistRadiusMm = cHalfWaist / Math.PI;

        for (int k = 0; k < rings; k++) {
            if (cHalf[k] > 1e-6) {
                radiusMm[k] = cHalf[k] / Math.PI;
            } else {
                radiusMm[k] = waistRadiusMm; // fallback, aby to nepadlo do stredu
            }
        }

        // For each ring, compute start arc-length per panel (sStart) so that theta mapping is consistent per ring
        double[][] sStart = new double[rings][panelCount];
        for (int k = 0; k < rings; k++) {
            double cum = 0.0;
            for (int i = 0; i < panelCount; i++) {
                sStart[k][i] = cum;
                if (valid[k][i]) {
                    cum += wPanel[k][i];
                }
            }
        }

        List<MeshView> out = new ArrayList<MeshView>();
        int cols = cfg.panelSubdiv + 1;

        for (int panelIndex = 0; panelIndex < panelCount; panelIndex++) {
            TriangleMesh mesh = new TriangleMesh();
            mesh.getTexCoords().addAll(0.0f, 0.0f);

            int[][] vid = new int[rings][cols];
            boolean[] ringOk = new boolean[rings];

            for (int k = 0; k < rings; k++) {
                ringOk[k] = valid[k][panelIndex];

                double C = cHalf[k];
                double rMm = radiusMm[k];
                double offset = cfg.offsetsMm[k];

                double y3 = -offset * cfg.scale;

                double denom = (C > 1e-9) ? C : cHalfWaist;

                double s0 = sStart[k][panelIndex];
                double w = valid[k][panelIndex] ? wPanel[k][panelIndex] : 0.0;
                double s1 = s0 + w;

                double theta0 = cfg.thetaStartRad + (s0 / denom) * Math.PI;
                double theta1 = cfg.thetaStartRad + (s1 / denom) * Math.PI;

                if (!valid[k][panelIndex]) {
                    int wk = waistRing;
                    double wC = cHalf[wk];
                    double wDen = (wC > 1e-9) ? wC : cHalfWaist;

                    double ws0 = sStart[wk][panelIndex];
                    double ww = valid[wk][panelIndex] ? wPanel[wk][panelIndex] : 0.0;
                    double ws1 = ws0 + ww;

                    theta0 = cfg.thetaStartRad + (ws0 / wDen) * Math.PI;
                    theta1 = cfg.thetaStartRad + (ws1 / wDen) * Math.PI;
                    rMm = waistRadiusMm;
                    y3 = 0.0;
                }

                for (int s = 0; s < cols; s++) {
                    double a = (double) s / (double) (cols - 1);
                    double th = lerp(theta0, theta1, a);

                    double x = (rMm * Math.cos(th)) * cfg.scale;
                    double z = (rMm * Math.sin(th)) * cfg.scale;

                    int base = mesh.getPoints().size() / 3;
                    mesh.getPoints().addAll((float) x, (float) y3, (float) z);
                    vid[k][s] = base;
                }
            }

            for (int k = 0; k < rings - 1; k++) {
                if (!ringOk[k] || !ringOk[k + 1]) {
                    continue;
                }
                for (int s = 0; s < cols - 1; s++) {
                    int v00 = vid[k][s];
                    int v01 = vid[k][s + 1];
                    int v10 = vid[k + 1][s];
                    int v11 = vid[k + 1][s + 1];

                    mesh.getFaces().addAll(v00, 0, v01, 0, v11, 0);
                    mesh.getFaces().addAll(v00, 0, v11, 0, v10, 0);
                }
            }

            MeshView mv = new MeshView(mesh);
            mv.setMaterial(new PhongMaterial(pickColor(panelIndex)));
            out.add(mv);
        }

        return out;
    }

    public static List<List<Point3D>> buildHalfCorsetRingPolylines(List<PanelCurves> panels, BuildConfig cfg) {
        List<List<Point3D>> out = new ArrayList<List<Point3D>>();
        if (panels == null || panels.isEmpty()) {
            return out;
        }

        int panelCount = panels.size();
        int rings = cfg.offsetsMm.length;
        int waistRing = findClosestToZero(cfg.offsetsMm);

        double[][] wPanel = new double[rings][panelCount];
        boolean[][] valid = new boolean[rings][panelCount];

        for (int k = 0; k < rings; k++) {
            double offset = cfg.offsetsMm[k];

            for (int i = 0; i < panelCount; i++) {
                PanelCurves p = panels.get(i);

                double waistY = estimateWaistY(p.getWaist());

                SeamPolyline leftSeam = SeamUtils.buildSeamPolyline(
                        toPts(p.getSeamToPrevUp()),
                        toPts(p.getSeamToPrevDown()),
                        waistY
                );
                SeamPolyline rightSeam = SeamUtils.buildSeamPolyline(
                        toPts(p.getSeamToNextUp()),
                        toPts(p.getSeamToNextDown()),
                        waistY
                );

                double targetY = waistY - offset;

                Pt2 L = sampleAtY(leftSeam, targetY);
                Pt2 R = sampleAtY(rightSeam, targetY);

                if (L == null || R == null) {
                    valid[k][i] = false;
                    wPanel[k][i] = 0.0;
                } else {
                    valid[k][i] = true;
                    wPanel[k][i] = R.minus(L).len();
                }
            }
        }

        double[] cHalf = new double[rings];
        for (int k = 0; k < rings; k++) {
            double sum = 0.0;
            for (int i = 0; i < panelCount; i++) {
                if (valid[k][i]) {
                    sum += wPanel[k][i];
                }
            }
            cHalf[k] = sum;
        }

        double cHalfWaist = cHalf[waistRing];
        if (cHalfWaist <= 1e-9) {
            throw new IllegalStateException("Waist half-circumference is zero.");
        }
        double waistRadiusMm = cHalfWaist / Math.PI;

        double[][] sStart = new double[rings][panelCount];
        for (int k = 0; k < rings; k++) {
            double cum = 0.0;
            for (int i = 0; i < panelCount; i++) {
                sStart[k][i] = cum;
                if (valid[k][i]) {
                    cum += wPanel[k][i];
                }
            }
        }

        int cols = cfg.panelSubdiv + 1;

        for (int k = 0; k < rings; k++) {
            double C = cHalf[k];
            double denom = (C > 1e-9) ? C : cHalfWaist;

            double rMm = (C > 1e-9) ? (C / Math.PI) : waistRadiusMm;

            double offset = cfg.offsetsMm[k];
            double y3 = -offset * cfg.scale;

            List<Point3D> ringPts = new ArrayList<Point3D>();

            for (int pi = 0; pi < panelCount; pi++) {
                if (!valid[k][pi]) {
                    continue;
                }

                double s0 = sStart[k][pi];
                double w = wPanel[k][pi];
                double s1 = s0 + w;

                double theta0 = cfg.thetaStartRad + (s0 / denom) * Math.PI;
                double theta1 = cfg.thetaStartRad + (s1 / denom) * Math.PI;

                for (int s = 0; s < cols; s++) {
                    double a = (double) s / (double) (cols - 1);
                    double th = lerp(theta0, theta1, a);

                    double x = (rMm * Math.cos(th)) * cfg.scale;
                    double z = (rMm * Math.sin(th)) * cfg.scale;

                    ringPts.add(new Point3D(x, y3, z));
                }
            }

            out.add(ringPts);
        }

        return out;
    }

    // ============================================================
    //  NEW / FIXED: EDGE OUTLINES PER PANEL (TOP / BOTTOM)
    //  - cp is sampled by arc-length
    //  - f is derived from parameter "a" (0..1), NOT from cp.x
    //  - robust segmentation via flushSegment
    // ============================================================
    public static List<List<Point3D>> buildEdgeOutlinesPerPanel(
            List<PanelCurves> panels,
            BuildConfig cfg,
            boolean topEdge,
            int samplesPerPanel
    ) {
        List<List<Point3D>> out = new ArrayList<List<Point3D>>();
        if (panels == null || panels.isEmpty()) {
            return out;
        }
        if (samplesPerPanel < 2) {
            throw new IllegalArgumentException("samplesPerPanel must be >= 2");
        }

        int panelCount = panels.size();

        SeamPolyline[] left = new SeamPolyline[panelCount];
        SeamPolyline[] right = new SeamPolyline[panelCount];
        double[] waistY = new double[panelCount];

        for (int i = 0; i < panelCount; i++) {
            PanelCurves p = panels.get(i);
            double wy = estimateWaistY(p.getWaist());
            waistY[i] = wy;

            left[i] = SeamUtils.buildSeamPolyline(
                    toPts(p.getSeamToPrevUp()),
                    toPts(p.getSeamToPrevDown()),
                    wy
            );
            right[i] = SeamUtils.buildSeamPolyline(
                    toPts(p.getSeamToNextUp()),
                    toPts(p.getSeamToNextDown()),
                    wy
            );
        }

        for (int i = 0; i < panelCount; i++) {
            PanelCurves p = panels.get(i);

            Curve2D edge = topEdge ? p.getTop() : p.getBottom();
            List<Pt> edgePts = (edge != null) ? edge.getPoints() : null;
            if (edgePts == null || edgePts.size() < 2) {
                // panel bez hrany -> nič
                continue;
            }

            // orientuj hranu zľava doprava (od left seam k right seam)
            List<Pt> oriented = orientEdgeLeftToRight(edgePts, left[i], right[i]);

            List<Point3D> current = new ArrayList<Point3D>();

            for (int s = 0; s < samplesPerPanel; s++) {
                double a = (double) s / (double) (samplesPerPanel - 1);

                // 1) robustný bod na edge podľa dĺžky (nie podľa indexu)
                Pt cp = samplePolylineByArcLength(oriented, a);
                if (cp == null) {
                    flushSegment(out, current);
                    continue;
                }

                double y = cp.getY();

                // 2) švy v tomto y
                Pt2 Li = sampleAtY(left[i], y);
                Pt2 Ri = sampleAtY(right[i], y);

                if (Li == null || Ri == null) {
                    flushSegment(out, current);
                    continue;
                }

                // normalizuj L/R podľa X (aby width nebolo záporné)
                double lx = Li.x;
                double rx = Ri.x;
                if (rx < lx) {
                    double tmp = lx;
                    lx = rx;
                    rx = tmp;
                }

                double wThis = rx - lx;
                if (wThis < 1e-6) {
                    flushSegment(out, current);
                    continue;
                }

                // 3) !!! FIX: f berieme priamo z parametra "a" (zľava -> doprava)
                double f = a;

                // 4) C(y) a cum(y) pre panel i
                double cum = 0.0;
                double C = 0.0;

                for (int j = 0; j < panelCount; j++) {
                    Pt2 Lj = sampleAtY(left[j], y);
                    Pt2 Rj = sampleAtY(right[j], y);
                    if (Lj == null || Rj == null) {
                        continue;
                    }

                    double jlx = Lj.x;
                    double jrx = Rj.x;
                    if (jrx < jlx) {
                        double tmp = jlx;
                        jlx = jrx;
                        jrx = tmp;
                    }

                    double wj = jrx - jlx;
                    if (wj < 1e-6) {
                        continue;
                    }

                    if (j < i) {
                        cum += wj;
                    }
                    C += wj;
                }

                if (C <= 1e-6) {
                    flushSegment(out, current);
                    continue;
                }

                double sPos = cum + f * wThis;
                double theta = cfg.thetaStartRad + (sPos / C) * Math.PI;
                double radiusMm = C / Math.PI;

                double wy = waistY[i];
                double offsetMm = wy - y; // y menšie => TOP => kladný offset
                double y3 = -offsetMm * cfg.scale;

                double x3 = (radiusMm * Math.cos(theta)) * cfg.scale;
                double z3 = (radiusMm * Math.sin(theta)) * cfg.scale;

                current.add(new Point3D(x3, y3, z3));
            }

            flushSegment(out, current);
        }

        return out;
    }

    // ============================================================
    // helpers
    // ============================================================
    private static void flushSegment(List<List<Point3D>> out, List<Point3D> current) {
        if (current.size() >= 2) {
            out.add(current);
        }
        current.clear();
    }

    private static List<Pt> orientEdgeLeftToRight(List<Pt> edgePts, SeamPolyline left, SeamPolyline right) {
        Pt first = edgePts.get(0);
        Pt last = edgePts.get(edgePts.size() - 1);

        Pt2 L0 = sampleAtY(left, first.getY());
        Pt2 R0 = sampleAtY(right, first.getY());
        Pt2 L1 = sampleAtY(left, last.getY());
        Pt2 R1 = sampleAtY(right, last.getY());

        if (L0 == null || R0 == null || L1 == null || R1 == null) {
            return edgePts;
        }

        double mid0 = 0.5 * (L0.x + R0.x);
        double mid1 = 0.5 * (L1.x + R1.x);

        boolean firstMoreRight = (first.getX() - mid0) > (last.getX() - mid1);
        if (!firstMoreRight) {
            return edgePts;
        }

        List<Pt> rev = new ArrayList<Pt>(edgePts);
        Collections.reverse(rev);
        return rev;
    }

    /**
     * Sample polyline at normalized arc-length parameter t01 in [0..1].
     */
    private static Pt samplePolylineByArcLength(List<Pt> pts, double t01) {
        if (pts == null || pts.size() < 2) {
            return null;
        }
        if (t01 <= 0.0) {
            return pts.get(0);
        }
        if (t01 >= 1.0) {
            return pts.get(pts.size() - 1);
        }

        double total = 0.0;
        for (int i = 0; i < pts.size() - 1; i++) {
            Pt a = pts.get(i);
            Pt b = pts.get(i + 1);
            double dx = b.getX() - a.getX();
            double dy = b.getY() - a.getY();
            total += Math.sqrt(dx * dx + dy * dy);
        }
        if (total < 1e-9) {
            return pts.get(0);
        }

        double target = t01 * total;
        double acc = 0.0;

        for (int i = 0; i < pts.size() - 1; i++) {
            Pt a = pts.get(i);
            Pt b = pts.get(i + 1);

            double dx = b.getX() - a.getX();
            double dy = b.getY() - a.getY();
            double seg = Math.sqrt(dx * dx + dy * dy);

            if (seg < 1e-12) {
                continue;
            }

            if (acc + seg >= target) {
                double u = (target - acc) / seg;
                double x = a.getX() + u * dx;
                double y = a.getY() + u * dy;

                // !!! PRISPÔSOB TU podľa tvojej implementácie Pt:
                // ak Pt nemá (x,y) ctor, zmeň na Pt.of(x,y) alebo new Pt(...).
                return new Pt(x, y);
            }
            acc += seg;
        }

        return pts.get(pts.size() - 1);
    }

    private static int findClosestToZero(double[] a) {
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

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static Pt2 sampleAtY(SeamPolyline seam, double targetY) {
        List<SeamUtils.Intersection> hits = SeamUtils.intersectHorizontal(seam.pts, targetY);
        return SeamUtils.pickClosestToWaist(hits, seam.waistParam);
    }

    private static double estimateWaistY(Curve2D waist) {
        List<Pt2> pts = toPts(waist);
        if (pts.size() < 2) {
            throw new IllegalArgumentException("WAIST curve too short.");
        }
        Pt2 a = pts.get(0);
        Pt2 b = pts.get(pts.size() - 1);
        return 0.5 * (a.y + b.y);
    }

    private static List<Pt2> toPts(Curve2D curve) {
        List<Pt> src = curve.getPoints();
        List<Pt2> out = new ArrayList<Pt2>(src.size());

        for (Pt p : src) {
            out.add(new Pt2(p.getX(), p.getY()));
        }
        return out;
    }

    private static Color pickColor(int idx) {
        Color[] palette = new Color[]{
            Color.RED, Color.ORANGE, Color.GREEN, Color.DODGERBLUE, Color.PURPLE, Color.BROWN
        };
        return palette[idx % palette.length];
    }

    public static List<MeshView> buildHalfCorsetMeshesTextured(
            List<PanelCurves> panels,
            BuildConfig cfg,
            java.util.function.Function<PanelCurves, javafx.scene.image.Image> textureForPanel
    ) {
        if (panels == null || panels.isEmpty()) {
            return new ArrayList<>();
        }
        if (cfg.panelSubdiv < 1) {
            throw new IllegalArgumentException("panelSubdiv must be >= 1");
        }

        int panelCount = panels.size();
        int rings = cfg.offsetsMm.length;
        int waistRing = findClosestToZero(cfg.offsetsMm);

        // --- (toto nechaj presne ako máš v kruhovej verzii) ---
        double[][] wPanel = new double[rings][panelCount];
        boolean[][] valid = new boolean[rings][panelCount];

        for (int k = 0; k < rings; k++) {
            double offset = cfg.offsetsMm[k];
            for (int i = 0; i < panelCount; i++) {
                PanelCurves p = panels.get(i);
                double waistY = estimateWaistY(p.getWaist());

                SeamPolyline leftSeam = SeamUtils.buildSeamPolyline(
                        toPts(p.getSeamToPrevUp()),
                        toPts(p.getSeamToPrevDown()),
                        waistY
                );
                SeamPolyline rightSeam = SeamUtils.buildSeamPolyline(
                        toPts(p.getSeamToNextUp()),
                        toPts(p.getSeamToNextDown()),
                        waistY
                );

                double targetY = waistY - offset;
                Pt2 L = sampleAtY(leftSeam, targetY);
                Pt2 R = sampleAtY(rightSeam, targetY);

                if (L == null || R == null) {
                    valid[k][i] = false;
                    wPanel[k][i] = 0.0;
                } else {
                    valid[k][i] = true;
                    wPanel[k][i] = R.minus(L).len();
                }
            }
        }

        double[] cHalf = new double[rings];
        double[] radiusMm = new double[rings];
        for (int k = 0; k < rings; k++) {
            double sum = 0.0;
            for (int i = 0; i < panelCount; i++) {
                if (valid[k][i]) {
                    sum += wPanel[k][i];
                }
            }
            cHalf[k] = sum;
        }

        double cHalfWaist = cHalf[waistRing];
        if (cHalfWaist <= 1e-9) {
            throw new IllegalStateException("Waist half-circumference is zero.");
        }
        double waistRadiusMm = cHalfWaist / Math.PI;

        for (int k = 0; k < rings; k++) {
            radiusMm[k] = (cHalf[k] > 1e-6) ? (cHalf[k] / Math.PI) : waistRadiusMm;
        }

        double[][] sStart = new double[rings][panelCount];
        for (int k = 0; k < rings; k++) {
            double cum = 0.0;
            for (int i = 0; i < panelCount; i++) {
                sStart[k][i] = cum;
                if (valid[k][i]) {
                    cum += wPanel[k][i];
                }
            }
        }
        // --- koniec “nezmenenej” časti ---

        List<MeshView> out = new ArrayList<>();
        int cols = cfg.panelSubdiv + 1;

        for (int panelIndex = 0; panelIndex < panelCount; panelIndex++) {
            TriangleMesh mesh = new TriangleMesh();

            // 1:1 mapping: každý vertex má svoj texCoord
            // takže texCoord index == vertex index
            int[][] vId = new int[rings][cols];
            boolean[] ringOk = new boolean[rings];

            for (int k = 0; k < rings; k++) {
                ringOk[k] = valid[k][panelIndex];

                double C = cHalf[k];
                double rMm = radiusMm[k];
                double offset = cfg.offsetsMm[k];
                double y3 = -offset * cfg.scale;

                double denom = (C > 1e-9) ? C : cHalfWaist;

                double s0 = sStart[k][panelIndex];
                double w = valid[k][panelIndex] ? wPanel[k][panelIndex] : 0.0;
                double s1 = s0 + w;

                double theta0 = cfg.thetaStartRad + (s0 / denom) * Math.PI;
                double theta1 = cfg.thetaStartRad + (s1 / denom) * Math.PI;

                if (!valid[k][panelIndex]) {
                    // degenerované na waist ring (ako máš)
                    int wk = waistRing;
                    double wC = cHalf[wk];
                    double wDen = (wC > 1e-9) ? wC : cHalfWaist;

                    double ws0 = sStart[wk][panelIndex];
                    double ww = valid[wk][panelIndex] ? wPanel[wk][panelIndex] : 0.0;
                    double ws1 = ws0 + ww;

                    theta0 = cfg.thetaStartRad + (ws0 / wDen) * Math.PI;
                    theta1 = cfg.thetaStartRad + (ws1 / wDen) * Math.PI;
                    rMm = waistRadiusMm;
                    y3 = 0.0;
                }

                for (int s = 0; s < cols; s++) {
                    double a = (double) s / (double) (cols - 1);
                    double th = lerp(theta0, theta1, a);

                    double x = (rMm * Math.cos(th)) * cfg.scale;
                    double z = (rMm * Math.sin(th)) * cfg.scale;

                    // UV:
                    float u = (float) a;
                    float v = (float) ((double) k / (double) (rings - 1));
                    // JavaFX textúry majú (0,0) hore, takže ak chceš “top=0”, nechaj tak,
                    // ak bude otočené, prehoď na (1 - v).
                    // v = 1.0f - v;

                    int idx = mesh.getPoints().size() / 3;
                    mesh.getPoints().addAll((float) x, (float) y3, (float) z);
                    mesh.getTexCoords().addAll(u, v);

                    vId[k][s] = idx;
                }
            }

            // faces: pointIndex, texIndex (tu sú rovnaké)
            for (int k = 0; k < rings - 1; k++) {
                if (!ringOk[k] || !ringOk[k + 1]) {
                    continue;
                }

                for (int s = 0; s < cols - 1; s++) {
                    int v00 = vId[k][s];
                    int v01 = vId[k][s + 1];
                    int v10 = vId[k + 1][s];
                    int v11 = vId[k + 1][s + 1];

                    mesh.getFaces().addAll(v00, v00, v01, v01, v11, v11);
                    mesh.getFaces().addAll(v00, v00, v11, v11, v10, v10);
                }
            }

            MeshView mv = new MeshView(mesh);

            PhongMaterial mat = new PhongMaterial(Color.WHITE);
            javafx.scene.image.Image img = textureForPanel.apply(panels.get(panelIndex));
            if (img != null) {
                mat.setDiffuseMap(img);
                // voliteľne:
                // mat.setSpecularColor(Color.color(0.15,0.15,0.15));
            } else {
                mat.setDiffuseColor(pickColor(panelIndex));
            }

            mv.setMaterial(mat);
            out.add(mv);
        }

        return out;
    }
}
