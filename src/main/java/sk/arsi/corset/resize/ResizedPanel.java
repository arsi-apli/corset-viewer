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
                // Resize panel-edge curves using point-level interpolation
                resizedTop = PanelResizer.resizePanelEdgeCurve(original.getTop(), leftShift, rightShift);
                resizedBottom = PanelResizer.resizePanelEdgeCurve(original.getBottom(), leftShift, rightShift);
                resizedWaist = PanelResizer.resizePanelEdgeCurve(original.getWaist(), leftShift, rightShift);
                
                // Resize seams uniformly using explicit side detection
                // seamToPrev* are on the left side, seamToNext* are on the right side
                resizedSeamToPrevUp = PanelResizer.resizeSeamCurve(original.getSeamToPrevUp(), leftShift);
                resizedSeamToPrevDown = PanelResizer.resizeSeamCurve(original.getSeamToPrevDown(), leftShift);
                resizedSeamToNextUp = PanelResizer.resizeSeamCurve(original.getSeamToNextUp(), rightShift);
                resizedSeamToNextDown = PanelResizer.resizeSeamCurve(original.getSeamToNextDown(), rightShift);
                break;
                
            case TOP:
                // Resize only TOP curve using point-level interpolation
                resizedTop = PanelResizer.resizePanelEdgeCurve(original.getTop(), leftShift, rightShift);
                resizedBottom = original.getBottom(); // unchanged
                resizedWaist = original.getWaist(); // unchanged
                
                // Resize only UP seams using explicit side detection
                resizedSeamToPrevUp = PanelResizer.resizeSeamCurve(original.getSeamToPrevUp(), leftShift);
                resizedSeamToPrevDown = original.getSeamToPrevDown(); // unchanged
                resizedSeamToNextUp = PanelResizer.resizeSeamCurve(original.getSeamToNextUp(), rightShift);
                resizedSeamToNextDown = original.getSeamToNextDown(); // unchanged
                break;
                
            case BOTTOM:
                // Resize only BOTTOM curve using point-level interpolation
                resizedTop = original.getTop(); // unchanged
                resizedBottom = PanelResizer.resizePanelEdgeCurve(original.getBottom(), leftShift, rightShift);
                resizedWaist = original.getWaist(); // unchanged
                
                // Resize only DOWN seams using explicit side detection
                resizedSeamToPrevUp = original.getSeamToPrevUp(); // unchanged
                resizedSeamToPrevDown = PanelResizer.resizeSeamCurve(original.getSeamToPrevDown(), leftShift);
                resizedSeamToNextUp = original.getSeamToNextUp(); // unchanged
                resizedSeamToNextDown = PanelResizer.resizeSeamCurve(original.getSeamToNextDown(), rightShift);
                break;
        }
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
