package sk.arsi.corset.resize;

import sk.arsi.corset.model.Curve2D;
import sk.arsi.corset.model.PanelCurves;
import sk.arsi.corset.model.Pt;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for resizing panel curves.
 * Implements GLOBAL resize mode which shifts curves horizontally (X-only).
 */
public final class PanelResizer {

    private PanelResizer() {
        // Utility class
    }

    /**
     * Resize a panel according to the specified mode and side shift.
     * 
     * @param panel original panel curves
     * @param mode resize mode (DISABLED, GLOBAL, TOP, etc.). If null, returns original panel.
     * @param sideShiftMm side shift amount in mm
     * @return new panel with resized curves, or original panel if mode is DISABLED or null, or if panel is null
     */
    public static PanelCurves resizePanel(PanelCurves panel, ResizeMode mode, double sideShiftMm) {
        if (panel == null || mode == null) {
            return panel;
        }

        // DISABLED mode: return original panel unchanged
        if (mode == ResizeMode.DISABLED) {
            return panel;
        }

        // GLOBAL mode: apply horizontal resize
        if (mode == ResizeMode.GLOBAL) {
            return new PanelCurves(
                panel.getPanelId(),
                resizeEdgeCurve(panel.getTop(), sideShiftMm),
                resizeEdgeCurve(panel.getBottom(), sideShiftMm),
                resizeEdgeCurve(panel.getWaist(), sideShiftMm),
                resizeSeamCurve(panel.getSeamToPrevUp(), -sideShiftMm),
                resizeSeamCurve(panel.getSeamToPrevDown(), -sideShiftMm),
                resizeSeamCurve(panel.getSeamToNextUp(), sideShiftMm),
                resizeSeamCurve(panel.getSeamToNextDown(), sideShiftMm)
            );
        }

        // TOP mode: shift only top endpoints and minY of up seams
        if (mode == ResizeMode.TOP) {
            return new PanelCurves(
                panel.getPanelId(),
                resizeTopEdgeCurveEndpointsOnly(panel.getTop(), sideShiftMm),
                panel.getBottom(), // unchanged
                panel.getWaist(),  // unchanged
                resizeSeamCurveTopOnly(panel.getSeamToPrevUp(), -sideShiftMm),
                panel.getSeamToPrevDown(), // unchanged
                resizeSeamCurveTopOnly(panel.getSeamToNextUp(), sideShiftMm),
                panel.getSeamToNextDown()  // unchanged
            );
        }

        // Other modes not yet implemented
        return panel;
    }

    /**
     * Calculate side shift for GLOBAL resize mode.
     * 
     * @param deltaMm desired full corset circumference change in mm
     * @param panelCount number of panels in the half-corset
     * @return side shift in mm for each panel edge
     */
    public static double calculateSideShift(double deltaMm, int panelCount) {
        if (panelCount <= 0) {
            return 0.0;
        }
        // deltaMm is full corset change
        // SVG is half corset
        // Each panel has two vertical seams
        return deltaMm / (4.0 * panelCount);
    }

    /**
     * Resize an edge curve (TOP, BOTTOM, or WAIST) for GLOBAL mode.
     * Applies point-level interpolation based on X coordinate:
     * - Leftmost points shift by -sideShiftMm
     * - Rightmost points shift by +sideShiftMm
     * - Intermediate points interpolate smoothly
     * 
     * @param curve original curve
     * @param sideShiftMm side shift amount in mm
     * @return new curve with resized X coordinates, Y unchanged
     */
    public static Curve2D resizeEdgeCurve(Curve2D curve, double sideShiftMm) {
        if (curve == null) {
            return null;
        }

        List<Pt> originalPoints = curve.getPoints();
        if (originalPoints == null || originalPoints.isEmpty()) {
            return curve;
        }

        // Find X range
        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        for (Pt p : originalPoints) {
            if (p != null) {
                double x = p.getX();
                if (Double.isFinite(x)) {
                    minX = Math.min(minX, x);
                    maxX = Math.max(maxX, x);
                }
            }
        }

        if (!Double.isFinite(minX) || !Double.isFinite(maxX) || minX == maxX) {
            return curve; // Cannot resize
        }

        double xRange = maxX - minX;

        // Transform each point
        List<Pt> resizedPoints = new ArrayList<>(originalPoints.size());
        for (Pt p : originalPoints) {
            if (p == null) {
                resizedPoints.add(null);
                continue;
            }

            double x = p.getX();
            double y = p.getY();

            if (!Double.isFinite(x) || !Double.isFinite(y)) {
                resizedPoints.add(p);
                continue;
            }

            // Calculate interpolation parameter t
            double t = (x - minX) / xRange;
            
            // Interpolate shift: lerp(-sideShiftMm, +sideShiftMm, t)
            double dx = -sideShiftMm + t * (2.0 * sideShiftMm);
            
            double newX = x + dx;
            resizedPoints.add(new Pt(newX, y));
        }

        return new Curve2D(curve.getId(), resizedPoints);
    }

    /**
     * Resize a seam curve for GLOBAL mode.
     * Applies uniform horizontal shift to all points.
     * 
     * @param curve original seam curve
     * @param shift horizontal shift in mm (negative for left, positive for right)
     * @return new curve with shifted X coordinates, Y unchanged
     */
    public static Curve2D resizeSeamCurve(Curve2D curve, double shift) {
        if (curve == null) {
            return null;
        }

        List<Pt> originalPoints = curve.getPoints();
        if (originalPoints == null || originalPoints.isEmpty()) {
            return curve;
        }

        List<Pt> resizedPoints = new ArrayList<>(originalPoints.size());
        for (Pt p : originalPoints) {
            if (p == null) {
                resizedPoints.add(null);
                continue;
            }

            double x = p.getX();
            double y = p.getY();

            if (!Double.isFinite(x) || !Double.isFinite(y)) {
                resizedPoints.add(p);
                continue;
            }

            resizedPoints.add(new Pt(x + shift, y));
        }

        return new Curve2D(curve.getId(), resizedPoints);
    }

    /**
     * Resize a seam curve for TOP mode.
     * Only shifts the minY point (top endpoint of the seam) horizontally.
     * This is used for UP seams to adjust only their top attachment point.
     * 
     * @param curve original seam curve (vertical)
     * @param shift horizontal shift in mm (negative for left, positive for right)
     * @return new curve with only the minY point shifted in X, rest unchanged
     */
    public static Curve2D resizeSeamCurveTopOnly(Curve2D curve, double shift) {
        if (curve == null) {
            return null;
        }

        List<Pt> originalPoints = curve.getPoints();
        if (originalPoints == null || originalPoints.isEmpty()) {
            return curve;
        }

        // Find the point with minimum Y (topmost point)
        // Note: Uses strict < to pick first occurrence in case of ties
        int minYIndex = -1;
        double minY = Double.POSITIVE_INFINITY;
        
        for (int i = 0; i < originalPoints.size(); i++) {
            Pt p = originalPoints.get(i);
            if (p != null && Double.isFinite(p.getY()) && p.getY() < minY) {
                minY = p.getY();
                minYIndex = i;
            }
        }

        // If no valid minY point found, return original curve
        if (minYIndex < 0) {
            return curve;
        }

        // Create new points list with only the minY point shifted
        List<Pt> resizedPoints = new ArrayList<>(originalPoints.size());
        for (int i = 0; i < originalPoints.size(); i++) {
            Pt p = originalPoints.get(i);
            if (i == minYIndex && p != null) {
                double x = p.getX();
                double y = p.getY();
                if (Double.isFinite(x) && Double.isFinite(y)) {
                    resizedPoints.add(new Pt(x + shift, y));
                } else {
                    resizedPoints.add(p);
                }
            } else {
                resizedPoints.add(p);
            }
        }

        return new Curve2D(curve.getId(), resizedPoints);
    }

    /**
     * Resize the TOP edge curve for TOP mode.
     * Only shifts the leftmost and rightmost endpoints horizontally.
     * Interior points remain unchanged.
     * 
     * @param curve original TOP edge curve
     * @param sideShiftMm side shift amount in mm
     * @return new curve with only endpoints shifted: left by -sideShiftMm, right by +sideShiftMm
     */
    public static Curve2D resizeTopEdgeCurveEndpointsOnly(Curve2D curve, double sideShiftMm) {
        if (curve == null) {
            return null;
        }

        List<Pt> originalPoints = curve.getPoints();
        if (originalPoints == null || originalPoints.isEmpty()) {
            return curve;
        }

        // Find indices of leftmost and rightmost points
        // Note: Uses strict < for minX (picks first occurrence in case of ties)
        // and strict > for maxX (picks last occurrence in case of ties)
        int minXIndex = -1;
        int maxXIndex = -1;
        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        
        for (int i = 0; i < originalPoints.size(); i++) {
            Pt p = originalPoints.get(i);
            if (p != null && Double.isFinite(p.getX())) {
                double x = p.getX();
                if (x < minX) {
                    minX = x;
                    minXIndex = i;
                }
                if (x > maxX) {
                    maxX = x;
                    maxXIndex = i;
                }
            }
        }

        // If no valid endpoints found, return original curve
        if (minXIndex < 0 || maxXIndex < 0) {
            return curve;
        }

        // Create new points list with only endpoints shifted
        List<Pt> resizedPoints = new ArrayList<>(originalPoints.size());
        for (int i = 0; i < originalPoints.size(); i++) {
            Pt p = originalPoints.get(i);
            if (p == null) {
                resizedPoints.add(null);
                continue;
            }

            double x = p.getX();
            double y = p.getY();

            if (!Double.isFinite(x) || !Double.isFinite(y)) {
                resizedPoints.add(p);
                continue;
            }

            if (i == minXIndex) {
                // Leftmost point: shift left by -sideShiftMm
                resizedPoints.add(new Pt(x - sideShiftMm, y));
            } else if (i == maxXIndex) {
                // Rightmost point: shift right by +sideShiftMm
                resizedPoints.add(new Pt(x + sideShiftMm, y));
            } else {
                // Interior points: no change
                resizedPoints.add(p);
            }
        }

        return new Curve2D(curve.getId(), resizedPoints);
    }
}
