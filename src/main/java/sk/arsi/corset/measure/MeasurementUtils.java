package sk.arsi.corset.measure;

import sk.arsi.corset.model.Curve2D;
import sk.arsi.corset.model.PanelCurves;
import sk.arsi.corset.model.Pt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.OptionalDouble;

public final class MeasurementUtils {

    // Small epsilon for floating-point comparisons in intersection calculations.
    // This helps handle numerical precision issues and cases where segment endpoints
    // are very close to (but not exactly at) the measurement line.
    private static final double EPSILON = 1e-6;

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

    // -------------------- Curve length --------------------
    /**
     * Compute the total length of a curve as a polyline.
     * 
     * @param curve The curve to measure
     * @return Total length of the curve, or 0.0 if invalid
     */
    public static double computeCurveLength(Curve2D curve) {
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

            double dx = x1 - x0;
            double dy = y1 - y0;
            length += Math.sqrt(dx * dx + dy * dy);
        }

        return length;
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
     * Uses epsilon tolerance to handle floating-point precision issues.
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
            
            // Skip horizontal segments (within epsilon)
            if (Math.abs(y1 - y0) < EPSILON) {
                continue;
            }
            
            // Check if y is between y0 and y1 (with epsilon tolerance)
            double minY = Math.min(y0, y1) - EPSILON;
            double maxY = Math.max(y0, y1) + EPSILON;
            boolean between = y >= minY && y <= maxY;
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
     * Left boundary = seamToPrev (prefer based on direction) Right boundary =
     * seamToNext (prefer based on direction)
     * For positive dyMm (upwards): prefer UP curves, fallback to DOWN
     * For negative dyMm (downwards): prefer DOWN curves, fallback to UP
     */
    public static OptionalDouble computePanelWidthAtDy(PanelCurves panel, double dyMm) {
        if (panel == null) {
            return OptionalDouble.empty();
        }

        double waistY = computePanelWaistY0(panel.getWaist());
        double y = waistY - dyMm;

        // Choose curves based on direction:
        // For positive dyMm (measuring upwards), prefer UP curves
        // For negative dyMm (measuring downwards), prefer DOWN curves
        Curve2D left, right;
        if (dyMm >= 0) {
            // Measuring upwards: prefer UP curves
            left = preferNonEmpty(panel.getSeamToPrevUp(), panel.getSeamToPrevDown());
            right = preferNonEmpty(panel.getSeamToNextUp(), panel.getSeamToNextDown());
        } else {
            // Measuring downwards: prefer DOWN curves
            left = preferNonEmpty(panel.getSeamToPrevDown(), panel.getSeamToPrevUp());
            right = preferNonEmpty(panel.getSeamToNextDown(), panel.getSeamToNextUp());
        }
        
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

    /**
     * Compute half waist circumference by summing the lengths of all panel waist curves.
     * This is used when measuring exactly at the waist (dyMm == 0).
     * 
     * @param panels List of panels
     * @return Sum of waist curve lengths
     */
    public static double computeHalfWaistCircumference(List<PanelCurves> panels) {
        if (panels == null || panels.isEmpty()) {
            return 0.0;
        }

        double sum = 0.0;
        for (PanelCurves p : panels) {
            if (p != null && p.getWaist() != null) {
                sum += computeCurveLength(p.getWaist());
            }
        }
        return sum;
    }

    /**
     * Compute full waist circumference (2 × sum of panel waist curve lengths).
     * This is used when measuring exactly at the waist (dyMm == 0).
     * 
     * @param panels List of panels
     * @return Full waist circumference
     */
    public static double computeFullWaistCircumference(List<PanelCurves> panels) {
        return 2.0 * computeHalfWaistCircumference(panels);
    }

    public static double computeFullCircumference(List<PanelCurves> panels, double dyMm) {
        // Use waist curve length for measurements within ±5mm of the waist.
        // This dead-zone provides stability near the waist where seam intersection
        // results can be unreliable due to numerical precision issues and nearly-
        // horizontal curve segments around dy ≈ 0.
        // 
        // Rationale:
        // - For some SVG patterns, measurements are unreliable for small |dy| values
        //   (users report fluctuations of ±4mm) but stabilize at larger distances.
        // - Using the waist curve length directly for |dy| < 5mm ensures consistent
        //   measurements in this critical region while maintaining intersection-based
        //   accuracy elsewhere.
        if (Math.abs(dyMm) < 5.0) {
            return computeFullWaistCircumference(panels);
        }
        // For |dy| >= 5mm, use intersection-based circumference
        return 2.0 * computeHalfCircumference(panels, dyMm);
    }

    /**
     * Represents the valid range for dyMm measurements.
     * 
     * This immutable class encapsulates the maximum measurable distances
     * from the waist where all panels in a corset have measurable widths.
     * The range is asymmetric because UP curves (above waist) and DOWN curves
     * (below waist) may have different extents.
     * 
     * This class is part of the public API and is returned by
     * {@link #computeValidDyRange(List, double)}.
     */
    public static final class DyRange {
        private final double maxUpDy;    // Maximum positive dyMm (upwards)
        private final double maxDownDy;  // Maximum absolute negative dyMm (downwards, stored as positive)

        public DyRange(double maxUpDy, double maxDownDy) {
            this.maxUpDy = maxUpDy;
            this.maxDownDy = maxDownDy;
        }

        /**
         * @return Maximum distance upward from waist where measurements are valid (positive value in mm)
         */
        public double getMaxUpDy() {
            return maxUpDy;
        }

        /**
         * @return Maximum distance downward from waist where measurements are valid (positive value in mm)
         */
        public double getMaxDownDy() {
            return maxDownDy;
        }
    }

    // Maximum distance to search for valid measurement range in mm
    private static final double MAX_DY_SEARCH_DISTANCE = 1000.0;
    
    // Minimum step size for dy range computation in mm
    private static final double MIN_STEP_SIZE = 0.5;
    
    // Dead-zone around waist where measurements may be unreliable due to seam intersection issues.
    // Matches the dead-zone used in computeFullCircumference to ensure consistency.
    private static final double DEAD_ZONE_MM = 5.0;

    /**
     * Computes the valid dy range where ALL panels have measurable width.
     * Skips a dead-zone around the waist (±DEAD_ZONE_MM) to avoid unreliable measurements
     * near dy=0 where seam intersection may be unstable but works at larger distances.
     * 
     * @param panels List of panels to measure
     * @param stepMm Step size for sampling (default 2mm recommended)
     * @return DyRange with maxUpDy (positive) and maxDownDy (absolute value of max negative)
     */
    public static DyRange computeValidDyRange(List<PanelCurves> panels, double stepMm) {
        if (panels == null || panels.isEmpty()) {
            return new DyRange(0.0, 0.0);
        }

        // Ensure step is positive and reasonable
        stepMm = Math.max(MIN_STEP_SIZE, Math.abs(stepMm));

        // Find maximum upward range (positive dyMm)
        // Start search at max(DEAD_ZONE_MM, stepMm) to skip the unreliable region near waist
        double startDyUp = Math.max(DEAD_ZONE_MM, stepMm);
        double maxUpDy = 0.0;
        boolean foundValidUp = false;
        
        // First, find the first valid dy >= DEAD_ZONE_MM
        for (double dy = startDyUp; dy <= MAX_DY_SEARCH_DISTANCE; dy += stepMm) {
            boolean allValid = true;
            for (PanelCurves panel : panels) {
                OptionalDouble width = computePanelWidthAtDy(panel, dy);
                if (width.isEmpty()) {
                    allValid = false;
                    break;
                }
            }
            if (allValid) {
                foundValidUp = true;
                maxUpDy = dy;
                break;
            }
        }
        
        // If we found a valid point, continue scanning to find the maximum valid range
        if (foundValidUp) {
            for (double dy = maxUpDy + stepMm; dy <= MAX_DY_SEARCH_DISTANCE; dy += stepMm) {
                boolean allValid = true;
                for (PanelCurves panel : panels) {
                    OptionalDouble width = computePanelWidthAtDy(panel, dy);
                    if (width.isEmpty()) {
                        allValid = false;
                        break;
                    }
                }
                if (allValid) {
                    maxUpDy = dy;
                } else {
                    break; // Stop at first invalid step
                }
            }
        }

        // Find maximum downward range (negative dyMm)
        // Start search at min(-DEAD_ZONE_MM, -stepMm) to skip the unreliable region near waist
        double startDyDown = -Math.max(DEAD_ZONE_MM, stepMm);
        double maxDownDy = 0.0;
        boolean foundValidDown = false;
        
        // First, find the first valid dy <= -DEAD_ZONE_MM
        for (double dy = startDyDown; dy >= -MAX_DY_SEARCH_DISTANCE; dy -= stepMm) {
            boolean allValid = true;
            for (PanelCurves panel : panels) {
                OptionalDouble width = computePanelWidthAtDy(panel, dy);
                if (width.isEmpty()) {
                    allValid = false;
                    break;
                }
            }
            if (allValid) {
                foundValidDown = true;
                maxDownDy = Math.abs(dy);
                break;
            }
        }
        
        // If we found a valid point, continue scanning to find the maximum valid range
        if (foundValidDown) {
            for (double dy = -maxDownDy - stepMm; dy >= -MAX_DY_SEARCH_DISTANCE; dy -= stepMm) {
                boolean allValid = true;
                for (PanelCurves panel : panels) {
                    OptionalDouble width = computePanelWidthAtDy(panel, dy);
                    if (width.isEmpty()) {
                        allValid = false;
                        break;
                    }
                }
                if (allValid) {
                    maxDownDy = Math.abs(dy); // Store as positive
                } else {
                    break; // Stop at first invalid step
                }
            }
        }

        return new DyRange(maxUpDy, maxDownDy);
    }

    /**
     * Computes the valid dy range with default step size of 2mm.
     */
    public static DyRange computeValidDyRange(List<PanelCurves> panels) {
        return computeValidDyRange(panels, 2.0);
    }
}
