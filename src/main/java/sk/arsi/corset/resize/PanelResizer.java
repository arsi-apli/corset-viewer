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
        
        if (Math.abs(shiftX) < 0.001) {
            // No meaningful shift, return original
            return curve;
        }
        
        List<Pt> shiftedPoints = shiftPointsX(curve.getPoints(), shiftX);
        return new Curve2D(curve.getId(), shiftedPoints);
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
