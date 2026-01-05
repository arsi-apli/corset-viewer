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
                resizedTop = resizeCurveIfNotNull(original.getTop(), true);
                resizedBottom = resizeCurveIfNotNull(original.getBottom(), false);
                resizedWaist = resizeCurveIfNotNull(original.getWaist(), true);
                resizedSeamToPrevUp = resizeCurveIfNotNull(original.getSeamToPrevUp(), true);
                resizedSeamToPrevDown = resizeCurveIfNotNull(original.getSeamToPrevDown(), false);
                resizedSeamToNextUp = resizeCurveIfNotNull(original.getSeamToNextUp(), false);
                resizedSeamToNextDown = resizeCurveIfNotNull(original.getSeamToNextDown(), false);
                break;
                
            case TOP:
                // Resize only TOP curve and UP seams
                resizedTop = resizeCurveIfNotNull(original.getTop(), true);
                resizedBottom = original.getBottom(); // unchanged
                resizedWaist = original.getWaist(); // unchanged
                resizedSeamToPrevUp = resizeCurveIfNotNull(original.getSeamToPrevUp(), true);
                resizedSeamToPrevDown = original.getSeamToPrevDown(); // unchanged
                resizedSeamToNextUp = resizeCurveIfNotNull(original.getSeamToNextUp(), false);
                resizedSeamToNextDown = original.getSeamToNextDown(); // unchanged
                break;
                
            case BOTTOM:
                // Resize only BOTTOM curve and DOWN seams
                resizedTop = original.getTop(); // unchanged
                resizedBottom = resizeCurveIfNotNull(original.getBottom(), false);
                resizedWaist = original.getWaist(); // unchanged
                resizedSeamToPrevUp = original.getSeamToPrevUp(); // unchanged
                resizedSeamToPrevDown = resizeCurveIfNotNull(original.getSeamToPrevDown(), false);
                resizedSeamToNextUp = original.getSeamToNextUp(); // unchanged
                resizedSeamToNextDown = resizeCurveIfNotNull(original.getSeamToNextDown(), false);
                break;
        }
    }
    
    /**
     * Resize a curve if it's not null, determining which shift to use based on side.
     * 
     * @param curve Original curve
     * @param isLeft true if this is a left-side curve (seamToPrev), false for right-side (seamToNext)
     * @return Resized curve or original if null
     */
    private Curve2D resizeCurveIfNotNull(Curve2D curve, boolean isLeft) {
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
