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
        // (Placeholder for future implementation - for now return original)
        if (mode == ResizeMode.TOP) {
            // TODO: Implement TOP mode behavior
            return panel;
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
}
