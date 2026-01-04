package sk.arsi.corset.export;

import sk.arsi.corset.model.Curve2D;
import sk.arsi.corset.model.PanelCurves;
import sk.arsi.corset.model.PanelId;
import sk.arsi.corset.model.Pt;

import java.util.ArrayList;
import java.util.List;

/**
 * Service for generating sewing notches on panel seams.
 */
public final class NotchGenerator {

    /**
     * Generate notches for all panels.
     * 
     * @param panels List of panel curves
     * @param notchCount Number of notches per seam (e.g., 3)
     * @param notchLengthMm Length of each notch tick in mm (e.g., 4.0)
     * @return List of panel notches
     */
    public static List<PanelNotches> generateAllNotches(
            List<PanelCurves> panels,
            int notchCount,
            double notchLengthMm) {
        
        List<PanelNotches> allNotches = new ArrayList<>();
        
        for (PanelCurves panel : panels) {
            List<Notch> panelNotches = generatePanelNotches(panel, notchCount, notchLengthMm);
            allNotches.add(new PanelNotches(panel.getPanelId(), panelNotches));
        }
        
        return allNotches;
    }

    /**
     * Generate notches for a single panel.
     * Generates notches for both left (toPrev) and right (toNext) seams.
     */
    private static List<Notch> generatePanelNotches(
            PanelCurves panel,
            int notchCount,
            double notchLengthMm) {
        
        List<Notch> notches = new ArrayList<>();
        
        // Compute panel interior reference point
        Pt interior = GeometryUtils.computePanelInterior(panel.getWaist());
        
        // Generate notches for left seam (toPrev)
        List<Notch> leftNotches = generateSeamNotches(
                panel.getSeamToPrevUp(),
                panel.getSeamToPrevDown(),
                interior,
                notchCount,
                notchLengthMm,
                panel.getPanelId().name(),
                getPrevPanelName(panel.getPanelId())
        );
        notches.addAll(leftNotches);
        
        // Generate notches for right seam (toNext)
        List<Notch> rightNotches = generateSeamNotches(
                panel.getSeamToNextUp(),
                panel.getSeamToNextDown(),
                interior,
                notchCount,
                notchLengthMm,
                panel.getPanelId().name(),
                getNextPanelName(panel.getPanelId())
        );
        notches.addAll(rightNotches);
        
        return notches;
    }

    /**
     * Generate notches for a single seam.
     * Notches are generated separately for UP and DOWN curves.
     * Each curve gets N notches independently positioned at i/(N+1) along its length.
     */
    private static List<Notch> generateSeamNotches(
            Curve2D upCurve,
            Curve2D downCurve,
            Pt interior,
            int notchCount,
            double notchLengthMm,
            String panelName,
            String neighborName) {
        
        List<Notch> notches = new ArrayList<>();
        
        // Generate notches for UP curve
        if (upCurve != null && upCurve.getPoints() != null && !upCurve.getPoints().isEmpty()) {
            List<Notch> upNotches = generateCurveNotches(
                    upCurve.getPoints(),
                    interior,
                    notchCount,
                    notchLengthMm,
                    panelName,
                    neighborName,
                    "UP"
            );
            notches.addAll(upNotches);
        }
        
        // Generate notches for DOWN curve
        if (downCurve != null && downCurve.getPoints() != null && !downCurve.getPoints().isEmpty()) {
            List<Notch> downNotches = generateCurveNotches(
                    downCurve.getPoints(),
                    interior,
                    notchCount,
                    notchLengthMm,
                    panelName,
                    neighborName,
                    "DOWN"
            );
            notches.addAll(downNotches);
        }
        
        return notches;
    }

    /**
     * Generate notches for a single curve segment (UP or DOWN).
     */
    private static List<Notch> generateCurveNotches(
            List<Pt> curvePoints,
            Pt interior,
            int notchCount,
            double notchLengthMm,
            String panelName,
            String neighborName,
            String segment) {
        
        List<Notch> notches = new ArrayList<>();
        
        if (curvePoints == null || curvePoints.isEmpty()) {
            return notches;
        }
        
        // Generate notch positions
        List<Double> positions = GeometryUtils.generateNotchPositions(notchCount);
        
        // Generate a notch at each position
        for (int i = 0; i < positions.size(); i++) {
            double percentage = positions.get(i);
            
            // Find point on curve at this percentage
            Pt seamPoint = GeometryUtils.pointAtArcLength(curvePoints, percentage);
            if (seamPoint == null) {
                continue;
            }
            
            // Find tangent at this position
            Pt tangent = GeometryUtils.tangentAtArcLength(curvePoints, percentage);
            if (tangent == null) {
                continue;
            }
            
            // Compute inward normal
            Pt inwardNormal = GeometryUtils.computeInwardNormal(seamPoint, tangent, interior);
            
            // Create notch tick: from seam point inward by notchLengthMm
            Pt notchEnd = new Pt(
                    seamPoint.getX() + inwardNormal.getX() * notchLengthMm,
                    seamPoint.getY() + inwardNormal.getY() * notchLengthMm
            );
            
            // Generate notch ID with segment identifier (UP or DOWN)
            int percentInt = (int) Math.round(percentage * 100);
            String notchId = String.format("%s_NOTCH_%s_%s_%d",
                    panelName,
                    neighborName != null ? neighborName : "EDGE",
                    segment,
                    percentInt);
            
            notches.add(new Notch(seamPoint, notchEnd, notchId));
        }
        
        return notches;
    }

    /**
     * Get the name of the previous panel (for ID generation).
     */
    private static String getPrevPanelName(PanelId id) {
        if (id == null) {
            return null;
        }
        switch (id) {
            case A:
                return "A"; // AA - outer edge
            case B:
                return "A";
            case C:
                return "B";
            case D:
                return "C";
            case E:
                return "D";
            case F:
                return "E";
            default:
                return null;
        }
    }

    /**
     * Get the name of the next panel (for ID generation).
     */
    private static String getNextPanelName(PanelId id) {
        if (id == null) {
            return null;
        }
        switch (id) {
            case A:
                return "B";
            case B:
                return "C";
            case C:
                return "D";
            case D:
                return "E";
            case E:
                return "F";
            case F:
                return "F"; // FF - outer edge
            default:
                return null;
        }
    }
}
