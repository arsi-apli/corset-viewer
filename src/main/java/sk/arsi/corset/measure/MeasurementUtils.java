package sk.arsi.corset.measure;

import sk.arsi.corset.model.Curve2D;
import sk.arsi.corset.model.PanelCurves;
import sk.arsi.corset.model.Pt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Utility class for corset panel measurements.
 * No JavaFX dependencies - pure geometry calculations.
 */
public final class MeasurementUtils {

    private MeasurementUtils() {
        // utility class
    }

    /**
     * Compute the waist reference Y coordinate for a panel.
     * Uses the median Y value of all waist curve points.
     * 
     * @param waist the waist curve
     * @return median Y coordinate, or 0.0 if curve is null/empty
     */
    public static double computePanelWaistY0(Curve2D waist) {
        if (waist == null || waist.getPoints() == null || waist.getPoints().isEmpty()) {
            return 0.0;
        }

        List<Pt> points = waist.getPoints();
        List<Double> yValues = new ArrayList<>();
        for (Pt p : points) {
            if (p != null && Double.isFinite(p.getY())) {
                yValues.add(p.getY());
            }
        }

        if (yValues.isEmpty()) {
            return 0.0;
        }

        Collections.sort(yValues);
        int size = yValues.size();
        if (size % 2 == 0) {
            return (yValues.get(size / 2 - 1) + yValues.get(size / 2)) / 2.0;
        } else {
            return yValues.get(size / 2);
        }
    }

    /**
     * Compute panel width at a given height relative to waist.
     * Width is the horizontal distance between left seam (seamToPrev) and right seam (seamToNext)
     * at y = waistY - dyMm.
     * 
     * @param panel the panel
     * @param dyMm height offset from waist (positive = up, negative = down)
     * @return width in mm, or 0.0 if cannot be determined
     */
    public static double computePanelWidthAtDy(PanelCurves panel, double dyMm) {
        if (panel == null) {
            return 0.0;
        }

        double waistY = computePanelWaistY0(panel.getWaist());
        double targetY = waistY - dyMm;

        // Get X coordinates where seams intersect the target height
        // seamToPrev = left seam, seamToNext = right seam
        // Use UP curves for intersection calculation
        Curve2D leftSeam = panel.getSeamToPrevUp();
        Curve2D rightSeam = panel.getSeamToNextUp();

        Double leftX = findXAtY(leftSeam, targetY);
        Double rightX = findXAtY(rightSeam, targetY);

        if (leftX == null || rightX == null) {
            return 0.0;
        }

        return Math.abs(rightX - leftX);
    }

    /**
     * Find X coordinate where a curve intersects a horizontal line at given Y.
     * Uses linear interpolation between consecutive points.
     * 
     * @param curve the curve
     * @param targetY the Y coordinate
     * @return X coordinate at intersection, or null if no intersection found
     */
    private static Double findXAtY(Curve2D curve, double targetY) {
        if (curve == null || curve.getPoints() == null || curve.getPoints().size() < 2) {
            return null;
        }

        List<Pt> points = curve.getPoints();
        
        for (int i = 0; i < points.size() - 1; i++) {
            Pt p0 = points.get(i);
            Pt p1 = points.get(i + 1);
            
            if (p0 == null || p1 == null) {
                continue;
            }
            
            double y0 = p0.getY();
            double y1 = p1.getY();
            
            if (!Double.isFinite(y0) || !Double.isFinite(y1)) {
                continue;
            }
            
            // Check if targetY is between y0 and y1
            if ((y0 <= targetY && targetY <= y1) || (y1 <= targetY && targetY <= y0)) {
                double x0 = p0.getX();
                double x1 = p1.getX();
                
                if (!Double.isFinite(x0) || !Double.isFinite(x1)) {
                    continue;
                }
                
                // Linear interpolation
                if (Math.abs(y1 - y0) < 1e-9) {
                    // Horizontal segment, return average X
                    return (x0 + x1) / 2.0;
                }
                
                double t = (targetY - y0) / (y1 - y0);
                return x0 + t * (x1 - x0);
            }
        }
        
        return null;
    }

    /**
     * Compute half circumference at a given height relative to waist.
     * Sum of panel widths for panels A through F.
     * 
     * @param panels list of panels (should be sorted A-F)
     * @param dyMm height offset from waist (positive = up, negative = down)
     * @return half circumference in mm
     */
    public static double computeHalfCircumference(List<PanelCurves> panels, double dyMm) {
        if (panels == null || panels.isEmpty()) {
            return 0.0;
        }

        double sum = 0.0;
        for (PanelCurves panel : panels) {
            sum += computePanelWidthAtDy(panel, dyMm);
        }
        return sum;
    }

    /**
     * Compute full circumference at a given height relative to waist.
     * 
     * @param panels list of panels (should be sorted A-F)
     * @param dyMm height offset from waist (positive = up, negative = down)
     * @return full circumference in mm (2 * half circumference)
     */
    public static double computeFullCircumference(List<PanelCurves> panels, double dyMm) {
        return 2.0 * computeHalfCircumference(panels, dyMm);
    }

    /**
     * Compute the length of a curve as a polyline.
     * 
     * @param curve the curve
     * @return total length in mm
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

            double dx = p1.getX() - p0.getX();
            double dy = p1.getY() - p0.getY();

            if (!Double.isFinite(dx) || !Double.isFinite(dy)) {
                continue;
            }

            length += Math.sqrt(dx * dx + dy * dy);
        }

        return length;
    }

    /**
     * Compute the length of a curve portion above or below waist Y.
     * 
     * @param curve the curve
     * @param waistY the waist Y coordinate
     * @param above true for above waist (y < waistY), false for below (y >= waistY)
     * @return length of the portion in mm
     */
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

            double y0 = p0.getY();
            double y1 = p1.getY();

            if (!Double.isFinite(y0) || !Double.isFinite(y1)) {
                continue;
            }

            double x0 = p0.getX();
            double x1 = p1.getX();

            if (!Double.isFinite(x0) || !Double.isFinite(x1)) {
                continue;
            }

            // Check if segment crosses waistY
            boolean p0Above = y0 < waistY;
            boolean p1Above = y1 < waistY;

            if (p0Above == p1Above) {
                // Segment entirely on one side
                if (p0Above == above) {
                    // Segment is on the side we're measuring
                    double dx = x1 - x0;
                    double dy = y1 - y0;
                    length += Math.sqrt(dx * dx + dy * dy);
                }
            } else {
                // Segment crosses waistY, split it
                double t = (waistY - y0) / (y1 - y0);
                double xSplit = x0 + t * (x1 - x0);

                if (p0Above == above) {
                    // First part is on the side we're measuring
                    double dx = xSplit - x0;
                    double dy = waistY - y0;
                    length += Math.sqrt(dx * dx + dy * dy);
                } else {
                    // Second part is on the side we're measuring
                    double dx = x1 - xSplit;
                    double dy = y1 - waistY;
                    length += Math.sqrt(dx * dx + dy * dy);
                }
            }
        }

        return length;
    }

    /**
     * Result of seam length computation.
     */
    public static final class SeamLengths {
        private final String seamName;
        private final double upAbove;
        private final double upBelow;
        private final double downAbove;
        private final double downBelow;

        public SeamLengths(String seamName, double upAbove, double upBelow, double downAbove, double downBelow) {
            this.seamName = seamName;
            this.upAbove = upAbove;
            this.upBelow = upBelow;
            this.downAbove = downAbove;
            this.downBelow = downBelow;
        }

        public String getSeamName() {
            return seamName;
        }

        public double getUpAbove() {
            return upAbove;
        }

        public double getUpBelow() {
            return upBelow;
        }

        public double getDownAbove() {
            return downAbove;
        }

        public double getDownBelow() {
            return downBelow;
        }
    }

    /**
     * Compute seam lengths for a pair of panels.
     * Returns lengths for UP and DOWN curves, split at waist (above/below).
     * 
     * @param fromPanel the source panel
     * @param toPanel the target panel
     * @param seamName name of the seam (e.g., "AB")
     * @return seam lengths object
     */
    public static SeamLengths computeSeamLengths(PanelCurves fromPanel, PanelCurves toPanel, String seamName) {
        if (fromPanel == null) {
            return new SeamLengths(seamName, 0.0, 0.0, 0.0, 0.0);
        }

        // Use fromPanel's waist as reference
        double waistY = computePanelWaistY0(fromPanel.getWaist());

        // seamToNextUp = UP curve from fromPanel
        // seamToNextDown = DOWN curve from fromPanel
        Curve2D upCurve = fromPanel.getSeamToNextUp();
        Curve2D downCurve = fromPanel.getSeamToNextDown();

        double upAbove = computeCurveLengthPortion(upCurve, waistY, true);
        double upBelow = computeCurveLengthPortion(upCurve, waistY, false);
        double downAbove = computeCurveLengthPortion(downCurve, waistY, true);
        double downBelow = computeCurveLengthPortion(downCurve, waistY, false);

        return new SeamLengths(seamName, upAbove, upBelow, downAbove, downBelow);
    }
}
