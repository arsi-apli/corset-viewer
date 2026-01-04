package sk.arsi.corset.export;

import sk.arsi.corset.model.Curve2D;
import sk.arsi.corset.model.Pt;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility methods for geometric calculations related to export.
 */
public final class GeometryUtils {

    private GeometryUtils() {
        // Utility class
    }

    /**
     * Combine two curves (UP and DOWN) into a single continuous polyline.
     * UP curve is assumed to start at waist and go upward.
     * DOWN curve is assumed to start at waist and go downward.
     * The combined curve goes from top of UP, through waist, to bottom of DOWN.
     */
    public static List<Pt> combineCurves(Curve2D upCurve, Curve2D downCurve) {
        List<Pt> combined = new ArrayList<>();
        
        if (upCurve != null && upCurve.getPoints() != null) {
            List<Pt> upPoints = upCurve.getPoints();
            // Add UP curve in reverse order (top to waist)
            for (int i = upPoints.size() - 1; i >= 0; i--) {
                combined.add(upPoints.get(i));
            }
        }
        
        if (downCurve != null && downCurve.getPoints() != null) {
            List<Pt> downPoints = downCurve.getPoints();
            // Add DOWN curve in normal order (waist to bottom)
            // Skip first point if we already added the waist from UP curve
            int startIdx = (upCurve != null && upCurve.getPoints() != null && !upCurve.getPoints().isEmpty()) ? 1 : 0;
            for (int i = startIdx; i < downPoints.size(); i++) {
                combined.add(downPoints.get(i));
            }
        }
        
        return combined;
    }

    /**
     * Compute total arc length of a polyline.
     */
    public static double computeArcLength(List<Pt> points) {
        if (points == null || points.size() < 2) {
            return 0.0;
        }
        
        double length = 0.0;
        for (int i = 0; i < points.size() - 1; i++) {
            Pt p0 = points.get(i);
            Pt p1 = points.get(i + 1);
            if (p0 != null && p1 != null) {
                double dx = p1.getX() - p0.getX();
                double dy = p1.getY() - p0.getY();
                length += Math.sqrt(dx * dx + dy * dy);
            }
        }
        return length;
    }

    /**
     * Find a point at a given arc-length percentage along a polyline.
     * Returns null if the polyline is invalid or the percentage is out of range.
     * 
     * @param points The polyline points
     * @param percentage Arc-length percentage (0.0 to 1.0)
     * @return The point at the given percentage, or null if invalid
     */
    public static Pt pointAtArcLength(List<Pt> points, double percentage) {
        if (points == null || points.size() < 2) {
            return null;
        }
        if (percentage < 0.0 || percentage > 1.0) {
            return null;
        }
        
        double totalLength = computeArcLength(points);
        if (totalLength <= 0.0) {
            return null;
        }
        
        double targetLength = totalLength * percentage;
        double currentLength = 0.0;
        
        for (int i = 0; i < points.size() - 1; i++) {
            Pt p0 = points.get(i);
            Pt p1 = points.get(i + 1);
            if (p0 == null || p1 == null) {
                continue;
            }
            
            double dx = p1.getX() - p0.getX();
            double dy = p1.getY() - p0.getY();
            double segmentLength = Math.sqrt(dx * dx + dy * dy);
            
            if (currentLength + segmentLength >= targetLength) {
                // Target point is on this segment
                double t = (targetLength - currentLength) / segmentLength;
                double x = p0.getX() + t * dx;
                double y = p0.getY() + t * dy;
                return new Pt(x, y);
            }
            
            currentLength += segmentLength;
        }
        
        // If we reached here, return the last point
        return points.get(points.size() - 1);
    }

    /**
     * Compute the tangent vector at a given arc-length percentage along a polyline.
     * The tangent is normalized to unit length.
     * 
     * @param points The polyline points
     * @param percentage Arc-length percentage (0.0 to 1.0)
     * @return The normalized tangent vector as a Pt, or null if invalid
     */
    public static Pt tangentAtArcLength(List<Pt> points, double percentage) {
        if (points == null || points.size() < 2) {
            return null;
        }
        if (percentage < 0.0 || percentage > 1.0) {
            return null;
        }
        
        double totalLength = computeArcLength(points);
        if (totalLength <= 0.0) {
            return null;
        }
        
        double targetLength = totalLength * percentage;
        double currentLength = 0.0;
        
        for (int i = 0; i < points.size() - 1; i++) {
            Pt p0 = points.get(i);
            Pt p1 = points.get(i + 1);
            if (p0 == null || p1 == null) {
                continue;
            }
            
            double dx = p1.getX() - p0.getX();
            double dy = p1.getY() - p0.getY();
            double segmentLength = Math.sqrt(dx * dx + dy * dy);
            
            if (currentLength + segmentLength >= targetLength || i == points.size() - 2) {
                // Target point is on this segment, return normalized tangent
                if (segmentLength > 0.0) {
                    return new Pt(dx / segmentLength, dy / segmentLength);
                }
            }
            
            currentLength += segmentLength;
        }
        
        // Fallback: use last segment
        Pt p0 = points.get(points.size() - 2);
        Pt p1 = points.get(points.size() - 1);
        double dx = p1.getX() - p0.getX();
        double dy = p1.getY() - p0.getY();
        double length = Math.sqrt(dx * dx + dy * dy);
        if (length > 0.0) {
            return new Pt(dx / length, dy / length);
        }
        
        return null;
    }

    /**
     * Compute an interior reference point for a panel.
     * This is used to determine which side of a seam is "inward" (toward panel interior).
     * We use the centroid of the waist curve as a simple approximation.
     * 
     * @param waistCurve The waist curve of the panel
     * @return The interior reference point (centroid of waist)
     */
    public static Pt computePanelInterior(Curve2D waistCurve) {
        if (waistCurve == null || waistCurve.getPoints() == null || waistCurve.getPoints().isEmpty()) {
            return new Pt(0, 0);
        }
        
        List<Pt> points = waistCurve.getPoints();
        double sumX = 0.0;
        double sumY = 0.0;
        int count = 0;
        
        for (Pt p : points) {
            if (p != null) {
                sumX += p.getX();
                sumY += p.getY();
                count++;
            }
        }
        
        if (count == 0) {
            return new Pt(0, 0);
        }
        
        return new Pt(sumX / count, sumY / count);
    }

    /**
     * Determine the inward normal direction at a point on a seam.
     * Given a tangent vector and an interior reference point, compute two possible normals
     * (+90 and -90 degrees from tangent) and choose the one that points more toward the interior.
     * 
     * @param point The point on the seam
     * @param tangent The tangent vector at that point (should be normalized)
     * @param interiorRef The panel interior reference point
     * @return The inward normal vector (normalized)
     */
    public static Pt computeInwardNormal(Pt point, Pt tangent, Pt interiorRef) {
        if (point == null || tangent == null || interiorRef == null) {
            return new Pt(0, 0);
        }
        
        // Two possible normals: rotate tangent by +90 and -90 degrees
        // +90 degrees: (x, y) -> (-y, x)
        // -90 degrees: (x, y) -> (y, -x)
        Pt normal1 = new Pt(-tangent.getY(), tangent.getX());
        Pt normal2 = new Pt(tangent.getY(), -tangent.getX());
        
        // Test which normal points more toward the interior
        // by checking which direction brings us closer to interiorRef
        double dx = interiorRef.getX() - point.getX();
        double dy = interiorRef.getY() - point.getY();
        
        // Dot product with each normal
        double dot1 = normal1.getX() * dx + normal1.getY() * dy;
        double dot2 = normal2.getX() * dx + normal2.getY() * dy;
        
        // Choose the normal with larger (more positive) dot product
        return (dot1 > dot2) ? normal1 : normal2;
    }

    /**
     * Generate notch positions as percentages along a seam.
     * For count=3, returns [0.25, 0.5, 0.75]
     * For count=n, returns positions at i/(n+1) for i=1..n
     * 
     * @param notchCount Number of notches to generate
     * @return List of arc-length percentages (0.0 to 1.0)
     */
    public static List<Double> generateNotchPositions(int notchCount) {
        List<Double> positions = new ArrayList<>();
        if (notchCount <= 0) {
            return positions;
        }
        
        for (int i = 1; i <= notchCount; i++) {
            double percentage = (double) i / (notchCount + 1);
            positions.add(percentage);
        }
        
        return positions;
    }
}
