package sk.arsi.corset.resize;

import sk.arsi.corset.model.Curve2D;
import sk.arsi.corset.model.PanelCurves;
import sk.arsi.corset.svg.PathSampler;

import java.util.ArrayList;
import java.util.List;

/**
 * Resizes panels by editing original SVG path data and re-sampling.
 */
public final class PanelResizer {

    private final PathSampler sampler;
    private final double flatnessMm;
    private final double resampleStepMm;

    public PanelResizer(PathSampler sampler, double flatnessMm, double resampleStepMm) {
        this.sampler = sampler;
        this.flatnessMm = flatnessMm;
        this.resampleStepMm = resampleStepMm;
    }

    /**
     * Apply resize to panels based on mode and delta.
     * 
     * @param originalPanels the original panels (unmodified)
     * @param mode resize mode
     * @param deltaMm resize delta in mm
     * @return resized panels (or originals if mode is DISABLED or delta is 0)
     */
    public List<PanelCurves> resize(List<PanelCurves> originalPanels, ResizeMode mode, double deltaMm) {
        if (mode == ResizeMode.DISABLED || Math.abs(deltaMm) < 1e-9) {
            // Return original panels unchanged
            return originalPanels;
        }

        int panelCount = originalPanels.size();
        double sideShiftMm = deltaMm / (4.0 * panelCount);

        List<PanelCurves> resized = new ArrayList<>();

        for (PanelCurves panel : originalPanels) {
            PanelCurves resizedPanel;
            
            if (mode == ResizeMode.TOP) {
                resizedPanel = resizeTopMode(panel, sideShiftMm);
            } else if (mode == ResizeMode.GLOBAL) {
                resizedPanel = resizeGlobalMode(panel, sideShiftMm);
            } else {
                resizedPanel = panel; // Should not happen
            }
            
            resized.add(resizedPanel);
        }

        return resized;
    }

    /**
     * TOP mode: only resize top edge and UP seams.
     * Leave waist, bottom, and DOWN seams unchanged.
     */
    private PanelCurves resizeTopMode(PanelCurves panel, double sideShiftMm) {
        // Resize top edge: shift left endpoint by -sideShiftMm, right endpoint by +sideShiftMm
        Curve2D top = resizeTopEdge(panel.getTop(), sideShiftMm);
        
        // Resize UP seams: shift minY endpoint
        Curve2D seamToPrevUp = resizeSeamUp(panel.getSeamToPrevUp(), -sideShiftMm);
        Curve2D seamToNextUp = resizeSeamUp(panel.getSeamToNextUp(), sideShiftMm);
        
        // Keep waist, bottom, and DOWN seams unchanged
        return new PanelCurves(
            panel.getPanelId(),
            top,
            panel.getBottom(),
            panel.getWaist(),
            seamToPrevUp,
            panel.getSeamToPrevDown(),
            seamToNextUp,
            panel.getSeamToNextDown()
        );
    }

    /**
     * GLOBAL mode: resize all edges and seams.
     */
    private PanelCurves resizeGlobalMode(PanelCurves panel, double sideShiftMm) {
        // Resize horizontal edges (top, bottom, waist): shift left/right endpoints
        Curve2D top = resizeHorizontalEdge(panel.getTop(), sideShiftMm);
        Curve2D bottom = resizeHorizontalEdge(panel.getBottom(), sideShiftMm);
        Curve2D waist = resizeHorizontalEdge(panel.getWaist(), sideShiftMm);
        
        // Resize vertical seams: shift all endpoints
        Curve2D seamToPrevUp = resizeVerticalSeam(panel.getSeamToPrevUp(), -sideShiftMm);
        Curve2D seamToPrevDown = resizeVerticalSeam(panel.getSeamToPrevDown(), -sideShiftMm);
        Curve2D seamToNextUp = resizeVerticalSeam(panel.getSeamToNextUp(), sideShiftMm);
        Curve2D seamToNextDown = resizeVerticalSeam(panel.getSeamToNextDown(), sideShiftMm);
        
        return new PanelCurves(
            panel.getPanelId(),
            top,
            bottom,
            waist,
            seamToPrevUp,
            seamToPrevDown,
            seamToNextUp,
            seamToNextDown
        );
    }

    /**
     * Resize top edge curve: find two endpoints with minY (or leftmost/rightmost among minY),
     * shift left by -shift and right by +shift.
     */
    private Curve2D resizeTopEdge(Curve2D curve, double shift) {
        String d = curve.getD();
        if (d == null || d.trim().isEmpty()) {
            return curve; // No SVG path data, return unchanged
        }

        int[] indices = SvgPathEditor.findTopEdgeEndpoints(d);
        int leftIndex = indices[0];
        int rightIndex = indices[1];

        if (leftIndex < 0 || rightIndex < 0) {
            return curve; // No endpoints found
        }

        // Apply shifts
        String modified = d;
        
        // If same index, only shift once
        if (leftIndex == rightIndex) {
            // Edge case: only one top endpoint, don't shift
            return curve;
        }
        
        // Shift left endpoint first (if leftIndex < rightIndex)
        // Need to be careful about index changes when modifying
        if (leftIndex < rightIndex) {
            modified = SvgPathEditor.modifyEndpoint(modified, leftIndex, -shift, 0.0);
            modified = SvgPathEditor.modifyEndpoint(modified, rightIndex, shift, 0.0);
        } else {
            modified = SvgPathEditor.modifyEndpoint(modified, rightIndex, shift, 0.0);
            modified = SvgPathEditor.modifyEndpoint(modified, leftIndex, -shift, 0.0);
        }

        return resampleCurve(curve.getId(), modified);
    }

    /**
     * Resize UP seam: find endpoint with minY and shift its X coordinate.
     */
    private Curve2D resizeSeamUp(Curve2D curve, double shiftX) {
        String d = curve.getD();
        if (d == null || d.trim().isEmpty()) {
            return curve;
        }

        int minYIndex = SvgPathEditor.findMinYEndpoint(d);
        if (minYIndex < 0) {
            return curve;
        }

        String modified = SvgPathEditor.modifyEndpoint(d, minYIndex, shiftX, 0.0);
        return resampleCurve(curve.getId(), modified);
    }

    /**
     * Resize horizontal edge (top, bottom, waist): shift leftmost and rightmost endpoints.
     */
    private Curve2D resizeHorizontalEdge(Curve2D curve, double shift) {
        String d = curve.getD();
        if (d == null || d.trim().isEmpty()) {
            return curve;
        }

        int[] indices = SvgPathEditor.findLeftRightEndpoints(d);
        int leftIndex = indices[0];
        int rightIndex = indices[1];

        if (leftIndex < 0 || rightIndex < 0) {
            return curve;
        }

        String modified = d;
        
        if (leftIndex == rightIndex) {
            // Only one endpoint
            return curve;
        }
        
        if (leftIndex < rightIndex) {
            modified = SvgPathEditor.modifyEndpoint(modified, leftIndex, -shift, 0.0);
            modified = SvgPathEditor.modifyEndpoint(modified, rightIndex, shift, 0.0);
        } else {
            modified = SvgPathEditor.modifyEndpoint(modified, rightIndex, shift, 0.0);
            modified = SvgPathEditor.modifyEndpoint(modified, leftIndex, -shift, 0.0);
        }

        return resampleCurve(curve.getId(), modified);
    }

    /**
     * Resize vertical seam: shift both top and bottom endpoints horizontally.
     * This provides a simple, predictable widening/narrowing of the seam.
     */
    private Curve2D resizeVerticalSeam(Curve2D curve, double shiftX) {
        String d = curve.getD();
        if (d == null || d.trim().isEmpty()) {
            return curve;
        }

        // For vertical seams in GLOBAL mode, shift both the topmost (minY) 
        // and bottommost (maxY) endpoints by shiftX to widen/narrow the panel
        int minYIndex = SvgPathEditor.findMinYEndpoint(d);
        int maxYIndex = SvgPathEditor.findMaxYEndpoint(d);
        
        if (minYIndex < 0 || maxYIndex < 0) {
            return curve;
        }
        
        String modified = d;
        
        // Shift top endpoint
        modified = SvgPathEditor.modifyEndpoint(modified, minYIndex, shiftX, 0.0);
        
        // Shift bottom endpoint (if different from top)
        if (maxYIndex != minYIndex) {
            modified = SvgPathEditor.modifyEndpoint(modified, maxYIndex, shiftX, 0.0);
        }
        
        return resampleCurve(curve.getId(), modified);
    }

    /**
     * Re-sample modified path data to create new Curve2D.
     */
    private Curve2D resampleCurve(String id, String d) {
        return sampler.samplePath(id, d, flatnessMm, resampleStepMm);
    }
}
