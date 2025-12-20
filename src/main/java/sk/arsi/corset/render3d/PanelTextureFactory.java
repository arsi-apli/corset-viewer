package sk.arsi.corset.render3d;

import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import sk.arsi.corset.geom.SeamUtils;
import sk.arsi.corset.geom.SeamUtils.Pt2;
import sk.arsi.corset.geom.SeamUtils.SeamPolyline;
import sk.arsi.corset.model.Curve2D;
import sk.arsi.corset.model.PanelCurves;
import sk.arsi.corset.model.Pt;

import java.util.ArrayList;
import java.util.List;

public final class PanelTextureFactory {

    private static final double FILL_ALPHA = 0.18;

    private static final double OUTLINE_W = 8.0;   // TOP/BOTTOM
    private static final double SEAM_W = 6.0;      // left/right seams
    private static final double WAIST_W = 4.0;     // dashed

    private static final Color FILL = Color.color(0, 0, 0, FILL_ALPHA);
    private static final Color OUTLINE = Color.color(0, 0, 0, 0.95);
    private static final Color SEAM = Color.color(0, 0, 0, 0.95);
    private static final Color WAIST = Color.color(0.85, 0.10, 0.10, 0.95);

    private PanelTextureFactory() {
    }

    public static Image buildPanelTexture(PanelCurves panel, RingLoftMeshBuilder.BuildConfig cfg, int sizePx) {
        if (panel == null) {
            throw new IllegalArgumentException("panel is null");
        }
        if (cfg == null || cfg.offsetsMm == null || cfg.offsetsMm.length < 2) {
            throw new IllegalArgumentException("cfg.offsetsMm must have at least 2 rings");
        }
        if (sizePx < 128) {
            sizePx = 128;
        }

        double waistY = estimateWaistY(panel.getWaist());

        SeamPolyline left = SeamUtils.buildSeamPolyline(
                toPts(panel.getSeamToPrevUp()),
                toPts(panel.getSeamToPrevDown()),
                waistY
        );
        SeamPolyline right = SeamUtils.buildSeamPolyline(
                toPts(panel.getSeamToNextUp()),
                toPts(panel.getSeamToNextDown()),
                waistY
        );

        final int rings = cfg.offsetsMm.length;
        double[] yRing = new double[rings];
        for (int k = 0; k < rings; k++) {
            yRing[k] = waistY - cfg.offsetsMm[k];
        }

        int W = sizePx;
        int H = sizePx;

        Canvas canvas = new Canvas(W, H);
        GraphicsContext g = canvas.getGraphicsContext2D();

        g.setLineCap(StrokeLineCap.ROUND);
        g.setLineJoin(StrokeLineJoin.ROUND);

        drawUvGrid(g, W, H);

        // Fill from seams (always valid)
        fillPanelUvShape(g, left, right, yRing, W, H);

        // Scanlines (optional)
        drawScanlines(g, W, H, 6);

        // Seams
        drawSeamPolylineMapped(g, left.pts, left, right, yRing, W, H, SEAM, SEAM_W);
        drawSeamPolylineMapped(g, right.pts, left, right, yRing, W, H, SEAM, SEAM_W);

        // Waist
        drawCurveMappedDashed(g, panel.getWaist(), left, right, yRing, W, H, WAIST, WAIST_W, 14.0, 10.0);

        // Try real top/bottom curves (if present)
        drawCurveMapped(g, panel.getTop(), left, right, yRing, W, H, OUTLINE, OUTLINE_W);
        drawCurveMapped(g, panel.getBottom(), left, right, yRing, W, H, OUTLINE, OUTLINE_W);

        // Fallback/guaranteed: top/bottom derived from seam validity (matches 3D)
        drawTopBottomFromSeams(g, left, right, yRing, W, H, OUTLINE, OUTLINE_W);

        // ID
        g.setFill(Color.color(0, 0, 0, 0.65));
        g.fillText(String.valueOf(panel.getPanelId()), 12, 20);

        SnapshotParameters sp = new SnapshotParameters();
        sp.setFill(Color.TRANSPARENT);
        return canvas.snapshot(sp, null);
    }

    // ============================================================
    // Guaranteed TOP/BOTTOM from seams (matches 3D)
    // ============================================================
    private static void drawTopBottomFromSeams(
            GraphicsContext g,
            SeamPolyline left,
            SeamPolyline right,
            double[] yRing,
            int W,
            int H,
            Color color,
            double lineWidth
    ) {
        double[] tb = findSafeTopBottomY(left, right, yRing);
        double yTop = tb[0];
        double yBot = tb[1];

        Double vTop = vOfY(yTop, yRing);
        Double vBot = vOfY(yBot, yRing);
        if (vTop == null || vBot == null) {
            return;
        }

        // Draw as UV polylines with constant V
        strokeHorizontalUv(g, vTop, W, H, color, lineWidth);
        strokeHorizontalUv(g, vBot, W, H, color, lineWidth);
    }

    /**
     * Finds safe yTop/yBot inside the ring stack where BOTH seams have
     * intersections. This is robust even if panel.getTop()/getBottom() are
     * missing or outside.
     */
    private static double[] findSafeTopBottomY(SeamPolyline left, SeamPolyline right, double[] yRing) {
        // We search within the ring range (top..bottom) and find the first y where both seams exist.
        // yRing typically goes bottom (largest y) -> top (smallest y) but not guaranteed.
        double yMin = Math.min(yRing[0], yRing[yRing.length - 1]);
        double yMax = Math.max(yRing[0], yRing[yRing.length - 1]);

        // sample many Y positions
        int samples = 200;
        double yTop = Double.NaN;
        double yBot = Double.NaN;

        // find top: scan from yMin -> yMax depending on which is "top" in SVG (smaller y)
        // In SVG, smaller y is higher.
        // So top should be near yMin.
        for (int i = 0; i <= samples; i++) {
            double y = yMin + (yMax - yMin) * ((double) i / (double) samples);
            if (sampleAtY(left, y) != null && sampleAtY(right, y) != null) {
                yTop = y;
                break;
            }
        }

        // find bottom: scan from yMax -> yMin
        for (int i = 0; i <= samples; i++) {
            double y = yMax - (yMax - yMin) * ((double) i / (double) samples);
            if (sampleAtY(left, y) != null && sampleAtY(right, y) != null) {
                yBot = y;
                break;
            }
        }

        // fallback if somehow not found: clamp to ring ends
        if (!Double.isFinite(yTop)) {
            yTop = yMin;
        }
        if (!Double.isFinite(yBot)) {
            yBot = yMax;
        }

        // add tiny inset so we are not exactly on problematic horizontal segments
        double inset = 0.001 * (yMax - yMin);
        yTop = yTop + inset;
        yBot = yBot - inset;

        return new double[]{yTop, yBot};
    }

    private static void strokeHorizontalUv(GraphicsContext g, double v, int W, int H, Color color, double lineWidth) {
        g.setStroke(color);
        g.setLineWidth(lineWidth);

        double y = clamp01(v) * (H - 1);

        g.beginPath();
        g.moveTo(0, y);

        int steps = 80;
        for (int i = 1; i <= steps; i++) {
            double u = (double) i / (double) steps;
            double x = u * (W - 1);
            g.lineTo(x, y);
        }
        g.stroke();
    }

    // ============================================================
    // Existing drawing helpers
    // ============================================================
    private static void drawUvGrid(GraphicsContext g, int W, int H) {
        g.setLineWidth(1.0);

        g.setStroke(Color.color(0, 0, 0, 0.05));
        for (int i = 1; i < 10; i++) {
            double u = i / 10.0;
            double x = u * (W - 1);
            g.strokeLine(x, 0, x, H - 1);

            double v = i / 10.0;
            double y = v * (H - 1);
            g.strokeLine(0, y, W - 1, y);
        }

        g.setStroke(Color.color(0, 0, 0, 0.12));
        g.strokeRect(0.5, 0.5, W - 1, H - 1);
    }

    private static void fillPanelUvShape(
            GraphicsContext g,
            SeamPolyline left,
            SeamPolyline right,
            double[] yRing,
            int W,
            int H
    ) {
        List<double[]> uvLeft = seamToUv(left.pts, left, right, yRing);
        List<double[]> uvRight = seamToUv(right.pts, left, right, yRing);

        if (uvLeft.size() < 2 || uvRight.size() < 2) {
            return;
        }

        List<double[]> poly = new ArrayList<>(uvLeft.size() + uvRight.size());
        poly.addAll(uvLeft);
        for (int i = uvRight.size() - 1; i >= 0; i--) {
            poly.add(uvRight.get(i));
        }

        if (poly.size() < 3) {
            return;
        }

        double[] xs = new double[poly.size()];
        double[] ys = new double[poly.size()];

        for (int i = 0; i < poly.size(); i++) {
            double u = clamp01(poly.get(i)[0]);
            double v = clamp01(poly.get(i)[1]);
            xs[i] = u * (W - 1);
            ys[i] = v * (H - 1);
        }

        g.setFill(FILL);
        g.fillPolygon(xs, ys, poly.size());
    }

    private static void drawScanlines(GraphicsContext g, int W, int H, int lines) {
        if (lines <= 0) {
            return;
        }

        g.setStroke(Color.color(0, 0, 0, 0.22));
        g.setLineWidth(2.0);

        for (int i = 1; i <= lines; i++) {
            double v = (double) i / (double) (lines + 1);
            double y = v * (H - 1);

            g.beginPath();
            g.moveTo(0, y);
            g.lineTo(W - 1, y);
            g.stroke();
        }
    }

    private static void drawCurveMapped(
            GraphicsContext g,
            Curve2D curve,
            SeamPolyline left,
            SeamPolyline right,
            double[] yRing,
            int W,
            int H,
            Color color,
            double lineWidth
    ) {
        if (curve == null || curve.getPoints() == null || curve.getPoints().size() < 2) {
            return;
        }

        List<Pt> pts = curve.getPoints();
        List<double[]> uv = new ArrayList<>(pts.size());

        for (Pt p : pts) {
            double u = uOfXatY(p.getX(), p.getY(), left, right);
            Double v = vOfY(p.getY(), yRing);
            if (v == null) {
                continue;
            }
            uv.add(new double[]{u, v});
        }

        strokeUvPolyline(g, uv, W, H, color, lineWidth);
    }

    private static void drawCurveMappedDashed(
            GraphicsContext g,
            Curve2D curve,
            SeamPolyline left,
            SeamPolyline right,
            double[] yRing,
            int W,
            int H,
            Color color,
            double lineWidth,
            double dashLen,
            double gapLen
    ) {
        if (curve == null || curve.getPoints() == null || curve.getPoints().size() < 2) {
            return;
        }

        List<Pt> pts = curve.getPoints();
        List<double[]> uv = new ArrayList<>(pts.size());

        for (Pt p : pts) {
            double u = uOfXatY(p.getX(), p.getY(), left, right);
            Double v = vOfY(p.getY(), yRing);
            if (v == null) {
                continue;
            }
            uv.add(new double[]{u, v});
        }

        g.setLineDashes(dashLen, gapLen);
        strokeUvPolyline(g, uv, W, H, color, lineWidth);
        g.setLineDashes(null);
    }

    private static void drawSeamPolylineMapped(
            GraphicsContext g,
            List<Pt2> seamPts,
            SeamPolyline left,
            SeamPolyline right,
            double[] yRing,
            int W,
            int H,
            Color color,
            double lineWidth
    ) {
        if (seamPts == null || seamPts.size() < 2) {
            return;
        }

        List<double[]> uv = seamToUv(seamPts, left, right, yRing);
        strokeUvPolyline(g, uv, W, H, color, lineWidth);
    }

    private static List<double[]> seamToUv(List<Pt2> seamPts, SeamPolyline left, SeamPolyline right, double[] yRing) {
        List<double[]> uv = new ArrayList<>(seamPts.size());
        for (Pt2 p : seamPts) {
            double u = uOfXatY(p.x, p.y, left, right);
            Double v = vOfY(p.y, yRing);
            if (v == null) {
                continue;
            }
            uv.add(new double[]{u, v});
        }
        return uv;
    }

    private static void strokeUvPolyline(GraphicsContext g, List<double[]> uv, int W, int H, Color color, double lineWidth) {
        if (uv == null || uv.size() < 2) {
            return;
        }

        g.setStroke(color);
        g.setLineWidth(lineWidth);

        double[] first = uv.get(0);
        double x0 = clamp01(first[0]) * (W - 1);
        double y0 = clamp01(first[1]) * (H - 1);

        g.beginPath();
        g.moveTo(x0, y0);

        for (int i = 1; i < uv.size(); i++) {
            double[] p = uv.get(i);
            double x = clamp01(p[0]) * (W - 1);
            double y = clamp01(p[1]) * (H - 1);
            g.lineTo(x, y);
        }
        g.stroke();
    }

    // ============================================================
    // UV mapping core
    // ============================================================
    private static double uOfXatY(double x, double y, SeamPolyline left, SeamPolyline right) {
        Pt2 L = sampleAtY(left, y);
        Pt2 R = sampleAtY(right, y);
        if (L == null || R == null) {
            return 0.5;
        }

        double lx = L.x;
        double rx = R.x;
        if (rx < lx) {
            double t = lx;
            lx = rx;
            rx = t;
        }

        double w = rx - lx;
        if (w < 1e-9) {
            return 0.5;
        }

        return (x - lx) / w;
    }

    private static Double vOfY(double y, double[] yRing) {
        if (yRing == null || yRing.length < 2) {
            return null;
        }

        boolean decreasing = yRing[0] > yRing[yRing.length - 1];

        if (decreasing) {
            if (y >= yRing[0]) {
                return 0.0;
            }
            if (y <= yRing[yRing.length - 1]) {
                return 1.0;
            }
        } else {
            if (y <= yRing[0]) {
                return 0.0;
            }
            if (y >= yRing[yRing.length - 1]) {
                return 1.0;
            }
        }

        for (int k = 0; k < yRing.length - 1; k++) {
            double y0 = yRing[k];
            double y1 = yRing[k + 1];

            boolean inside = decreasing ? (y <= y0 && y >= y1) : (y >= y0 && y <= y1);
            if (!inside) {
                continue;
            }

            double denom = (y1 - y0);
            if (Math.abs(denom) < 1e-12) {
                return (double) k / (double) (yRing.length - 1);
            }

            double t = (y - y0) / denom;
            double v0 = (double) k / (double) (yRing.length - 1);
            double v1 = (double) (k + 1) / (double) (yRing.length - 1);
            return v0 + (v1 - v0) * t;
        }

        return 0.5;
    }

    // ============================================================
    // Geometry helpers
    // ============================================================
    private static Pt2 sampleAtY(SeamPolyline seam, double targetY) {
        List<SeamUtils.Intersection> hits = SeamUtils.intersectHorizontal(seam.pts, targetY);
        return SeamUtils.pickClosestToWaist(hits, seam.waistParam);
    }

    private static double estimateWaistY(Curve2D waist) {
        if (waist == null || waist.getPoints() == null || waist.getPoints().size() < 2) {
            throw new IllegalArgumentException("WAIST curve missing or too short.");
        }
        Pt a = waist.getFirst();
        Pt b = waist.getLast();
        return 0.5 * (a.getY() + b.getY());
    }

    private static List<Pt2> toPts(Curve2D curve) {
        if (curve == null || curve.getPoints() == null || curve.getPoints().size() < 2) {
            throw new IllegalArgumentException("Curve missing or too short.");
        }
        List<Pt> src = curve.getPoints();
        List<Pt2> out = new ArrayList<>(src.size());
        for (Pt p : src) {
            out.add(new Pt2(p.getX(), p.getY()));
        }
        return out;
    }

    private static double clamp01(double v) {
        if (v < 0.0) {
            return 0.0;
        }
        if (v > 1.0) {
            return 1.0;
        }
        return v;
    }
}
