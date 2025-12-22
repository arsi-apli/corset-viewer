package sk.arsi.corset.layout;

import sk.arsi.corset.model.Curve2D;
import sk.arsi.corset.model.PanelCurves;
import sk.arsi.corset.model.PanelId;
import sk.arsi.corset.model.Pt;

import java.util.List;

/**
 * Detects panel order (A→F vs F→A) from SVG geometry.
 * Uses panel A seams to determine orientation.
 */
public final class PanelOrderDetector {

    /**
     * Detect panel order from a list of panels.
     * 
     * @param panels List of panels (expected to contain panel A)
     * @return true if order is A→F, false if F→A
     */
    public boolean detectOrderAtoF(List<PanelCurves> panels) {
        if (panels == null || panels.isEmpty()) {
            return true; // default to A→F
        }

        // Find panel A
        PanelCurves panelA = null;
        for (PanelCurves p : panels) {
            if (p.getPanelId() == PanelId.A) {
                panelA = p;
                break;
            }
        }

        if (panelA == null) {
            return true; // default to A→F if panel A not found
        }

        // Get seams AA (seamToPrev) and AB (seamToNext)
        Curve2D seamAA = panelA.getSeamToPrevUp();
        Curve2D seamAB = panelA.getSeamToNextUp();

        if (seamAA == null || seamAB == null) {
            return true; // default to A→F if seams not found
        }

        // Compute average X position for each seam
        double avgXAA = computeAverageX(seamAA);
        double avgXAB = computeAverageX(seamAB);

        // If AB is to the right of AA, order is A→F; otherwise F→A
        return avgXAB > avgXAA;
    }

    /**
     * Compute average X coordinate of all points in a curve.
     */
    private double computeAverageX(Curve2D curve) {
        if (curve == null) {
            return 0.0;
        }

        List<Pt> points = curve.getPoints();
        if (points == null || points.isEmpty()) {
            return 0.0;
        }

        double sum = 0.0;
        int count = 0;

        for (Pt p : points) {
            if (p != null && Double.isFinite(p.getX())) {
                sum += p.getX();
                count++;
            }
        }

        return count > 0 ? sum / count : 0.0;
    }
}
