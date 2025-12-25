package sk.arsi.corset.measure;

import sk.arsi.corset.model.Curve2D;
import sk.arsi.corset.model.PanelCurves;
import sk.arsi.corset.model.Pt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.OptionalDouble;

public final class MeasurementUtils {

    private MeasurementUtils() {
    }

    public enum SeamSide {
        TO_PREV, // e.g. B->A
        TO_NEXT  // e.g. A->B
    }

    public static final class SeamSplit {

        public final double above; // from waist upwards (y < waistY)
        public final double below; // from waist downwards (y >= waistY)

        public SeamSplit(double above, double below) {
            this.above = above;
            this.below = below;
        }
    }

    // -------------------- Waist reference --------------------
    public static double computePanelWaistY0(Curve2D waist) {
        if (waist == null || waist.getPoints() == null || waist.getPoints().isEmpty()) {
            return 0.0;
        }
        List<Double> yValues = new ArrayList<>();
        for (Pt p : waist.getPoints()) {
            if (p != null && Double.isFinite(p.getY())) {
                yValues.add(p.getY());
            }
        }
        if (yValues.isEmpty()) {
            return 0.0;
        }

        Collections.sort(yValues);
        int n = yValues.size();
        if (n % 2 == 1) {
            return yValues.get(n / 2);
        }
        return (yValues.get(n / 2 - 1) + yValues.get(n / 2)) / 2.0;
    }

    // -------------------- Curve length split --------------------
    public static double computeCurveLengthPortion(Curve2D curve, double waistY, boolean above) {
        if (curve == null || curve.getPoints() == null || curve.getPoints().size() < 2) {
            return 0.0;
        }

        List<Pt> points = curve.getPoints();
        double length = 0.0;

        for (int i = 0; i < points.size() - 1; i++) {
            Pt p0 = points.get(i);
            Pt p1 = points.get(i + 1);
            if (p0 == null || p1 == null) {
                continue;
            }

            double x0 = p0.getX(), y0 = p0.getY();
            double x1 = p1.getX(), y1 = p1.getY();
            if (!Double.isFinite(x0) || !Double.isFinite(y0) || !Double.isFinite(x1) || !Double.isFinite(y1)) {
                continue;
            }

            boolean p0Above = y0 < waistY;
            boolean p1Above = y1 < waistY;

            if (p0Above == p1Above) {
                if (p0Above == above) {
                    double dx = x1 - x0;
                    double dy = y1 - y0;
                    length += Math.sqrt(dx * dx + dy * dy);
                }
            } else {
                // split at waistY
                double t = (waistY - y0) / (y1 - y0);
                double xs = x0 + t * (x1 - x0);

                if (p0Above == above) {
                    double dx = xs - x0;
                    double dy = waistY - y0;
                    length += Math.sqrt(dx * dx + dy * dy);
                } else {
                    double dx = x1 - xs;
                    double dy = y1 - waistY;
                    length += Math.sqrt(dx * dx + dy * dy);
                }
            }
        }

        return length;
    }

    private static Curve2D pickSeamCurve(PanelCurves p, SeamSide side, boolean upCurve) {
        if (p == null) {
            return null;
        }

        if (side == SeamSide.TO_NEXT) {
            return upCurve ? p.getSeamToNextUp() : p.getSeamToNextDown();
        } else {
            return upCurve ? p.getSeamToPrevUp() : p.getSeamToPrevDown();
        }
    }

    /**
     * Measures one seam curve on one panel, split at that panel's waist.
     *
     * @param panel panel
     * @param side TO_NEXT (A->B) or TO_PREV (B->A)
     * @param upCurve true = use ...Up curve, false = use ...Down curve
     */
    public static SeamSplit measureSeamSplitAtWaist(PanelCurves panel, SeamSide side, boolean upCurve) {
        if (panel == null) {
            return new SeamSplit(0.0, 0.0);
        }

        double waistY = computePanelWaistY0(panel.getWaist());
        Curve2D seam = pickSeamCurve(panel, side, upCurve);

        double above = computeCurveLengthPortion(seam, waistY, true);
        double below = computeCurveLengthPortion(seam, waistY, false);
        return new SeamSplit(above, below);
    }

    // -------------------- Circumference (A..F * 2) --------------------
    private static Curve2D preferNonEmpty(Curve2D primary, Curve2D fallback) {
        if (primary != null && primary.getPoints() != null && !primary.getPoints().isEmpty()) {
            return primary;
        }
        if (fallback != null && fallback.getPoints() != null && !fallback.getPoints().isEmpty()) {
            return fallback;
        }
        return null;
    }

    /**
     * Intersect polyline with horizontal line Y=y and return all X
     * intersections. Horizontal segments are ignored.
     */
    public static List<Double> intersectHorizontalXs(Curve2D curve, double y) {
        if (curve == null || curve.getPoints() == null || curve.getPoints().size() < 2) {
            return List.of();
        }

        List<Pt> pts = curve.getPoints();
        List<Double> xs = new ArrayList<>();

        for (int i = 0; i < pts.size() - 1; i++) {
            Pt a = pts.get(i);
            Pt b = pts.get(i + 1);
            if (a == null || b == null) {
                continue;
            }

            double y0 = a.getY();
            double y1 = b.getY();
            if (!Double.isFinite(y0) || !Double.isFinite(y1)) {
                continue;
            }
            if (y0 == y1) {
                continue; // skip horizontal
            }
            boolean between = y >= Math.min(y0, y1) && y <= Math.max(y0, y1);
            if (!between) {
                continue;
            }

            double t = (y - y0) / (y1 - y0);
            double x = a.getX() + t * (b.getX() - a.getX());
            if (Double.isFinite(x)) {
                xs.add(x);
            }
        }

        return xs;
    }

    private static OptionalDouble minXAtY(Curve2D curve, double y) {
        List<Double> xs = intersectHorizontalXs(curve, y);
        if (xs.isEmpty()) {
            return OptionalDouble.empty();
        }
        double m = xs.get(0);
        for (double v : xs) {
            m = Math.min(m, v);
        }
        return OptionalDouble.of(m);
    }

    private static OptionalDouble maxXAtY(Curve2D curve, double y) {
        List<Double> xs = intersectHorizontalXs(curve, y);
        if (xs.isEmpty()) {
            return OptionalDouble.empty();
        }
        double m = xs.get(0);
        for (double v : xs) {
            m = Math.max(m, v);
        }
        return OptionalDouble.of(m);
    }

    /**
     * Width of one panel at dyMm (0 at waist, + up, - down). Width = xRight -
     * xLeft at y = waistY - dyMm.
     *
     * Left boundary = seamToPrev (prefer Down, fallback Up) Right boundary =
     * seamToNext (prefer Down, fallback Up)
     */
    public static OptionalDouble computePanelWidthAtDy(PanelCurves panel, double dyMm) {
        if (panel == null) {
            return OptionalDouble.empty();
        }

        double waistY = computePanelWaistY0(panel.getWaist());
        double y = waistY - dyMm;

        // Prefer DOWN curves because often they are the "real" long seam. Fallback to UP.
        Curve2D left = preferNonEmpty(panel.getSeamToPrevDown(), panel.getSeamToPrevUp());
        Curve2D right = preferNonEmpty(panel.getSeamToNextDown(), panel.getSeamToNextUp());
        if (left == null || right == null) {
            return OptionalDouble.empty();
        }

        OptionalDouble xL = minXAtY(left, y);
        OptionalDouble xR = maxXAtY(right, y);
        if (xL.isEmpty() || xR.isEmpty()) {
            return OptionalDouble.empty();
        }

        double w = xR.getAsDouble() - xL.getAsDouble();
        return OptionalDouble.of(Math.abs(w));
    }

    /**
     * Half circumference (sum of panel widths). Full circumference = 2 * half.
     */
    public static double computeHalfCircumference(List<PanelCurves> panels, double dyMm) {
        if (panels == null || panels.isEmpty()) {
            return 0.0;
        }

        double sum = 0.0;
        for (PanelCurves p : panels) {
            OptionalDouble w = computePanelWidthAtDy(p, dyMm);
            if (w.isPresent()) {
                sum += w.getAsDouble();
            }
        }
        return sum;
    }

    public static double computeFullCircumference(List<PanelCurves> panels, double dyMm) {
        return 2.0 * computeHalfCircumference(panels, dyMm);
    }
}
