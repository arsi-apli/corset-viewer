package sk.arsi.corset.resize;

import sk.arsi.corset.model.Curve2D;
import sk.arsi.corset.model.PanelCurves;
import sk.arsi.corset.model.Pt;

import java.util.ArrayList;
import java.util.List;

/**
 * Computes resized panel geometry by shifting nodes in X only.
 * 
 * The resize is based on a desired full corset circumference change (deltaMm).
 * Since the input SVG represents half a corset, the per-panel shift is calculated as:
 * sideShiftMm = deltaMm / (4 * panelCount)
 * 
 * This is because:
 * - Half corset circumference change = deltaMm / 2
 * - Per-panel change = (deltaMm / 2) / panelCount
 * - Each panel has two vertical seams (left/right), so per-side shift = per-panel / 2
 * - Final: deltaMm / (2 * panelCount * 2) = deltaMm / (4 * panelCount)
 */
public final class PanelResizer {
    
    /** Threshold below which a shift is considered negligible */
    private static final double NEGLIGIBLE_SHIFT_THRESHOLD = 0.001;
    
    private PanelResizer() {
        // utility class
    }
    
    /**
     * Compute the per-side shift amount in mm for a given full circumference change.
     * 
     * @param deltaMm Desired full corset circumference change in mm (positive = grow, negative = shrink)
     * @param panelCount Total number of panels in the half corset
     * @return Per-side shift amount in mm
     */
    public static double computeSideShift(double deltaMm, int panelCount) {
        if (panelCount <= 0) {
            return 0.0;
        }
        return deltaMm / (4.0 * panelCount);
    }
    
    /**
     * Determine if a curve is on the "left" side of the panel.
     * This is based on the average X coordinate of the curve's points.
     * 
     * @param curve Curve to check
     * @param panel Panel containing the curve
     * @return true if curve is on left side, false otherwise
     */
    public static boolean isLeftSide(Curve2D curve, PanelCurves panel) {
        if (curve == null || curve.getPoints() == null || curve.getPoints().isEmpty()) {
            return false;
        }
        
        // Compute average X for the curve
        double avgX = computeAverageX(curve);
        
        // Compare with waist average X as reference
        Curve2D waist = panel.getWaist();
        if (waist == null || waist.getPoints() == null || waist.getPoints().isEmpty()) {
            return false;
        }
        
        double waistAvgX = computeAverageX(waist);
        
        // If curve's average X is less than waist average, it's on the left
        return avgX < waistAvgX;
    }
    
    /**
     * Compute average X coordinate of a curve's points.
     */
    private static double computeAverageX(Curve2D curve) {
        if (curve == null || curve.getPoints() == null || curve.getPoints().isEmpty()) {
            return 0.0;
        }
        
        double sumX = 0.0;
        int count = 0;
        
        for (Pt p : curve.getPoints()) {
            if (p != null && Double.isFinite(p.getX())) {
                sumX += p.getX();
                count++;
            }
        }
        
        return count > 0 ? sumX / count : 0.0;
    }
    
    /**
     * Apply X-only shift to a list of points.
     * 
     * @param points Original points
     * @param shiftX Amount to shift in X direction (in mm)
     * @return New list of shifted points
     */
    public static List<Pt> shiftPointsX(List<Pt> points, double shiftX) {
        if (points == null) {
            return null;
        }
        
        List<Pt> result = new ArrayList<>(points.size());
        for (Pt p : points) {
            if (p == null) {
                result.add(null);
            } else {
                result.add(new Pt(p.getX() + shiftX, p.getY()));
            }
        }
        return result;
    }
    
    /**
     * Create a resized curve by shifting its X coordinates.
     * 
     * @param curve Original curve
     * @param shiftX Amount to shift in X direction (in mm)
     * @return New curve with shifted points
     */
    public static Curve2D resizeCurve(Curve2D curve, double shiftX) {
        if (curve == null) {
            return null;
        }
        
        if (Math.abs(shiftX) < NEGLIGIBLE_SHIFT_THRESHOLD) {
            // No meaningful shift, return original
            return curve;
        }
        
        List<Pt> shiftedPoints = shiftPointsX(curve.getPoints(), shiftX);
        return new Curve2D(curve.getId(), shiftedPoints);
    }
    
    /**
     * Resize a panel-edge curve (TOP, BOTTOM, or WAIST) using point-level interpolation.
     * 
     * For curves that span the entire panel width, each point is shifted based on its
     * X position relative to the curve's extent. Leftmost points shift by leftShift,
     * rightmost points shift by rightShift, and points in-between are interpolated.
     * 
     * @param curve Original curve (e.g., TOP, BOTTOM, WAIST)
     * @param leftShift Shift amount for leftmost points (negative for grow, positive for shrink)
     * @param rightShift Shift amount for rightmost points (positive for grow, negative for shrink)
     * @return New curve with interpolated point shifts, or original if shifts are negligible
     */
    public static Curve2D resizePanelEdgeCurve(Curve2D curve, double leftShift, double rightShift) {
        if (curve == null || curve.getPoints() == null || curve.getPoints().isEmpty()) {
            return curve;
        }
        
        // Check if shifts are negligible
        if (Math.abs(leftShift) < NEGLIGIBLE_SHIFT_THRESHOLD && 
            Math.abs(rightShift) < NEGLIGIBLE_SHIFT_THRESHOLD) {
            return curve;
        }
        
        // Find min and max X coordinates in the curve
        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        
        for (Pt p : curve.getPoints()) {
            if (p != null && Double.isFinite(p.getX())) {
                minX = Math.min(minX, p.getX());
                maxX = Math.max(maxX, p.getX());
            }
        }
        
        // If no valid points or all points have same X, fall back to uniform shift
        if (!Double.isFinite(minX) || !Double.isFinite(maxX) || maxX - minX < NEGLIGIBLE_SHIFT_THRESHOLD) {
            // All points at same X - use average of left and right shifts
            double avgShift = (leftShift + rightShift) / 2.0;
            return resizeCurve(curve, avgShift);
        }
        
        // Apply interpolated shift to each point
        List<Pt> resizedPoints = new ArrayList<>(curve.getPoints().size());
        double xRange = maxX - minX;
        
        for (Pt p : curve.getPoints()) {
            if (p == null) {
                resizedPoints.add(null);
            } else if (!Double.isFinite(p.getX())) {
                resizedPoints.add(p);
            } else {
                // Compute interpolation factor t (0 at minX, 1 at maxX)
                double t = (p.getX() - minX) / xRange;
                // Clamp to [0, 1] for safety
                t = Math.max(0.0, Math.min(1.0, t));
                
                // Interpolate shift: leftShift at t=0, rightShift at t=1
                double shift = leftShift + t * (rightShift - leftShift);
                
                // Apply shift (Y unchanged)
                resizedPoints.add(new Pt(p.getX() + shift, p.getY()));
            }
        }
        
        return new Curve2D(curve.getId(), resizedPoints);
    }
    
    /**
     * Resize a seam curve by uniformly shifting all points.
     * 
     * Seam curves lie on one side of the panel and should be shifted uniformly.
     * 
     * @param curve Original seam curve
     * @param shift Shift amount in X direction
     * @return New curve with shifted points, or original if shift is negligible
     */
    public static Curve2D resizeSeamCurve(Curve2D curve, double shift) {
        if (curve == null) {
            return null;
        }
        
        if (Math.abs(shift) < NEGLIGIBLE_SHIFT_THRESHOLD) {
            return curve;
        }
        
        return resizeCurve(curve, shift);
    }
    
    /**
     * Create a resized panel with specified mode and shift amount.
     * 
     * @param panel Original panel
     * @param mode Resize mode (GLOBAL, TOP, or BOTTOM)
     * @param deltaMm Desired full corset circumference change in mm
     * @param panelCount Total number of panels in the half corset
     * @return New ResizedPanel instance
     */
    public static ResizedPanel resizePanel(PanelCurves panel, ResizeMode mode, double deltaMm, int panelCount) {
        if (panel == null || mode == null) {
            return null;
        }
        
        double sideShift = computeSideShift(deltaMm, panelCount);
        
        // Determine shifts for left and right sides
        // On grow (positive deltaMm): left side shifts left (negative), right side shifts right (positive)
        // On shrink (negative deltaMm): left side shifts right (positive), right side shifts left (negative)
        double leftShift = -sideShift;
        double rightShift = sideShift;
        
        return new ResizedPanel(panel, mode, leftShift, rightShift);
    }
}
