package sk.arsi.corset.resize;

import sk.arsi.corset.model.Curve2D;
import sk.arsi.corset.model.PanelCurves;

/**
 * A lightweight wrapper that provides resized geometry for a panel.
 * Does not mutate the original PanelCurves.
 */
public final class ResizedPanel {
    
    private final PanelCurves original;
    private final ResizeMode mode;
    private final double leftShift;
    private final double rightShift;
    
    // Cached resized curves
    private Curve2D resizedTop;
    private Curve2D resizedBottom;
    private Curve2D resizedWaist;
    private Curve2D resizedSeamToPrevUp;
    private Curve2D resizedSeamToPrevDown;
    private Curve2D resizedSeamToNextUp;
    private Curve2D resizedSeamToNextDown;
    
    public ResizedPanel(PanelCurves original, ResizeMode mode, double leftShift, double rightShift) {
        this.original = original;
        this.mode = mode;
        this.leftShift = leftShift;
        this.rightShift = rightShift;
        
        // Pre-compute resized curves
        computeResizedCurves();
    }
    
    private void computeResizedCurves() {
        // Determine which curves to resize based on mode
        switch (mode) {
            case GLOBAL:
                // Resize all curves
                resizedTop = resizeCurveIfNotNull(original.getTop());
                resizedBottom = resizeCurveIfNotNull(original.getBottom());
                resizedWaist = resizeCurveIfNotNull(original.getWaist());
                resizedSeamToPrevUp = resizeCurveIfNotNull(original.getSeamToPrevUp());
                resizedSeamToPrevDown = resizeCurveIfNotNull(original.getSeamToPrevDown());
                resizedSeamToNextUp = resizeCurveIfNotNull(original.getSeamToNextUp());
                resizedSeamToNextDown = resizeCurveIfNotNull(original.getSeamToNextDown());
                break;
                
            case TOP:
                // Resize only TOP curve and UP seams
                resizedTop = resizeCurveIfNotNull(original.getTop());
                resizedBottom = original.getBottom(); // unchanged
                resizedWaist = original.getWaist(); // unchanged
                resizedSeamToPrevUp = resizeCurveIfNotNull(original.getSeamToPrevUp());
                resizedSeamToPrevDown = original.getSeamToPrevDown(); // unchanged
                resizedSeamToNextUp = resizeCurveIfNotNull(original.getSeamToNextUp());
                resizedSeamToNextDown = original.getSeamToNextDown(); // unchanged
                break;
                
            case BOTTOM:
                // Resize only BOTTOM curve and DOWN seams
                resizedTop = original.getTop(); // unchanged
                resizedBottom = resizeCurveIfNotNull(original.getBottom());
                resizedWaist = original.getWaist(); // unchanged
                resizedSeamToPrevUp = original.getSeamToPrevUp(); // unchanged
                resizedSeamToPrevDown = resizeCurveIfNotNull(original.getSeamToPrevDown());
                resizedSeamToNextUp = original.getSeamToNextUp(); // unchanged
                resizedSeamToNextDown = resizeCurveIfNotNull(original.getSeamToNextDown());
                break;
        }
    }
    
    /**
     * Resize a curve if it's not null, determining which shift to use based on side.
     */
    private Curve2D resizeCurveIfNotNull(Curve2D curve) {
        if (curve == null) {
            return null;
        }
        
        // Determine actual side based on curve position
        boolean actuallyLeft = PanelResizer.isLeftSide(curve, original);
        double shift = actuallyLeft ? leftShift : rightShift;
        
        return PanelResizer.resizeCurve(curve, shift);
    }
    
    public PanelCurves getOriginal() {
        return original;
    }
    
    public ResizeMode getMode() {
        return mode;
    }
    
    public Curve2D getTop() {
        return resizedTop;
    }
    
    public Curve2D getBottom() {
        return resizedBottom;
    }
    
    public Curve2D getWaist() {
        return resizedWaist;
    }
    
    public Curve2D getSeamToPrevUp() {
        return resizedSeamToPrevUp;
    }
    
    public Curve2D getSeamToPrevDown() {
        return resizedSeamToPrevDown;
    }
    
    public Curve2D getSeamToNextUp() {
        return resizedSeamToNextUp;
    }
    
    public Curve2D getSeamToNextDown() {
        return resizedSeamToNextDown;
    }
}
