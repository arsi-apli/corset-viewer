package sk.arsi.corset.resize;

import sk.arsi.corset.model.Curve2D;
import sk.arsi.corset.model.Pt;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for resizing panel curves.
 * Implements GLOBAL and TOP resize modes which shift curves horizontally (X-only).
 */
public final class PanelResizer {

    private PanelResizer() {
        // Utility class
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
     * Find the index of the topmost point (minY) in a curve.
     * If multiple points have the same minY, choose based on preferLeft:
     * - For left seam (preferLeft=true): choose point with minX among minY points
     * - For right seam (preferLeft=false): choose point with maxX among minY points
     * 
     * @param curve the curve to search
     * @param preferLeft true for left seam (prefer minX), false for right seam (prefer maxX)
     * @return index of the topmost point, or -1 if not found
     */
    private static int findMinYPointIndex(Curve2D curve, boolean preferLeft) {
        if (curve == null) {
            return -1;
        }
        
        List<Pt> points = curve.getPoints();
        if (points == null || points.isEmpty()) {
            return -1;
        }
        
        double minY = Double.POSITIVE_INFINITY;
        int minYIndex = -1;
        double extremeX = preferLeft ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY;
        
        for (int i = 0; i < points.size(); i++) {
            Pt p = points.get(i);
            if (p == null || !Double.isFinite(p.getY()) || !Double.isFinite(p.getX())) {
                continue;
            }
            
            double y = p.getY();
            double x = p.getX();
            
            // Found a new minimum Y
            if (y < minY) {
                minY = y;
                minYIndex = i;
                extremeX = x;
            } 
            // Same Y, choose based on X preference
            else if (y == minY) {
                if ((preferLeft && x < extremeX) || (!preferLeft && x > extremeX)) {
                    minYIndex = i;
                    extremeX = x;
                }
            }
        }
        
        return minYIndex;
    }

    /**
     * Resize a seam curve for TOP mode.
     * Only shifts the topmost point (minY point) horizontally.
     * All other points remain unchanged.
     * 
     * @param curve original seam curve
     * @param shift horizontal shift in mm (negative for left, positive for right)
     * @param preferLeft true for left seam (prefer minX if multiple minY), false for right seam
     * @return new curve with only the topmost point shifted
     */
    public static Curve2D resizeSeamCurveTopOnly(Curve2D curve, double shift, boolean preferLeft) {
        if (curve == null) {
            return null;
        }
        
        List<Pt> originalPoints = curve.getPoints();
        if (originalPoints == null || originalPoints.isEmpty()) {
            return curve;
        }
        
        int minYIndex = findMinYPointIndex(curve, preferLeft);
        if (minYIndex < 0) {
            return curve; // No valid point found
        }
        
        List<Pt> resizedPoints = new ArrayList<>(originalPoints.size());
        for (int i = 0; i < originalPoints.size(); i++) {
            Pt p = originalPoints.get(i);
            if (p == null) {
                resizedPoints.add(null);
                continue;
            }
            
            if (i == minYIndex) {
                // Shift only the topmost point
                double x = p.getX();
                double y = p.getY();
                if (Double.isFinite(x) && Double.isFinite(y)) {
                    resizedPoints.add(new Pt(x + shift, y));
                } else {
                    resizedPoints.add(p);
                }
            } else {
                // Keep all other points unchanged
                resizedPoints.add(p);
            }
        }
        
        return new Curve2D(curve.getId(), resizedPoints);
    }

    /**
     * Resize the TOP edge curve for TOP mode.
     * Only shifts the two topmost endpoints (leftmost and rightmost points with minY).
     * All other points remain unchanged.
     * 
     * @param curve original TOP edge curve
     * @param sideShiftMm side shift amount in mm
     * @return new curve with only the top endpoints shifted
     */
    public static Curve2D resizeTopEdgeCurveEndpointsOnly(Curve2D curve, double sideShiftMm) {
        if (curve == null) {
            return null;
        }
        
        List<Pt> originalPoints = curve.getPoints();
        if (originalPoints == null || originalPoints.isEmpty()) {
            return curve;
        }
        
        // Find the two topmost endpoints (left and right)
        int leftTopIndex = findMinYPointIndex(curve, true);  // Prefer minX among minY points
        int rightTopIndex = findMinYPointIndex(curve, false); // Prefer maxX among minY points
        
        if (leftTopIndex < 0 || rightTopIndex < 0) {
            return curve; // No valid points found
        }
        
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
            
            if (i == leftTopIndex) {
                // Left top endpoint: shift by -sideShiftMm
                resizedPoints.add(new Pt(x - sideShiftMm, y));
            } else if (i == rightTopIndex) {
                // Right top endpoint: shift by +sideShiftMm
                resizedPoints.add(new Pt(x + sideShiftMm, y));
            } else {
                // Keep all other points unchanged
                resizedPoints.add(p);
            }
        }
        
        return new Curve2D(curve.getId(), resizedPoints);
    }
}
