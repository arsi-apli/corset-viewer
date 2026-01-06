package sk.arsi.corset.resize;

import sk.arsi.corset.model.Curve2D;
import sk.arsi.corset.model.PanelCurves;
import sk.arsi.corset.model.PanelId;

/**
 * Wrapper for a panel that provides resized geometry without mutating the original.
 * This allows the rendering code to switch between original and resized geometry.
 */
public final class ResizedPanel {

    private final PanelCurves originalPanel;
    private final ResizeMode mode;
    private final double sideShiftMm;
    
    // Cached resized curves (lazy initialization)
    private Curve2D resizedTop;
    private Curve2D resizedBottom;
    private Curve2D resizedWaist;
    private Curve2D resizedSeamToPrevUp;
    private Curve2D resizedSeamToPrevDown;
    private Curve2D resizedSeamToNextUp;
    private Curve2D resizedSeamToNextDown;

    /**
     * Create a resized panel wrapper.
     * 
     * @param originalPanel original panel curves
     * @param mode resize mode
     * @param sideShiftMm side shift amount for GLOBAL mode
     */
    public ResizedPanel(PanelCurves originalPanel, ResizeMode mode, double sideShiftMm) {
        this.originalPanel = originalPanel;
        this.mode = mode;
        this.sideShiftMm = sideShiftMm;
    }

    public PanelId getPanelId() {
        return originalPanel.getPanelId();
    }

    public Curve2D getTop() {
        if (resizedTop == null) {
            if (mode == ResizeMode.GLOBAL) {
                resizedTop = PanelResizer.resizeEdgeCurve(originalPanel.getTop(), sideShiftMm);
            } else if (mode == ResizeMode.TOP) {
                resizedTop = PanelResizer.resizeTopEdgeCurveEndpointsOnly(originalPanel.getTop(), sideShiftMm);
            }
        }
        return resizedTop != null ? resizedTop : originalPanel.getTop();
    }

    public Curve2D getBottom() {
        if (resizedBottom == null && mode == ResizeMode.GLOBAL) {
            resizedBottom = PanelResizer.resizeEdgeCurve(originalPanel.getBottom(), sideShiftMm);
        }
        return resizedBottom != null ? resizedBottom : originalPanel.getBottom();
    }

    public Curve2D getWaist() {
        if (resizedWaist == null && mode == ResizeMode.GLOBAL) {
            resizedWaist = PanelResizer.resizeEdgeCurve(originalPanel.getWaist(), sideShiftMm);
        }
        return resizedWaist != null ? resizedWaist : originalPanel.getWaist();
    }

    public Curve2D getSeamToPrevUp() {
        if (resizedSeamToPrevUp == null) {
            if (mode == ResizeMode.GLOBAL) {
                resizedSeamToPrevUp = PanelResizer.resizeSeamCurve(originalPanel.getSeamToPrevUp(), -sideShiftMm);
            } else if (mode == ResizeMode.TOP) {
                resizedSeamToPrevUp = PanelResizer.resizeSeamCurveTopOnly(originalPanel.getSeamToPrevUp(), -sideShiftMm, true);
            }
        }
        return resizedSeamToPrevUp != null ? resizedSeamToPrevUp : originalPanel.getSeamToPrevUp();
    }

    public Curve2D getSeamToPrevDown() {
        if (resizedSeamToPrevDown == null && mode == ResizeMode.GLOBAL) {
            resizedSeamToPrevDown = PanelResizer.resizeSeamCurve(originalPanel.getSeamToPrevDown(), -sideShiftMm);
        }
        return resizedSeamToPrevDown != null ? resizedSeamToPrevDown : originalPanel.getSeamToPrevDown();
    }

    public Curve2D getSeamToNextUp() {
        if (resizedSeamToNextUp == null) {
            if (mode == ResizeMode.GLOBAL) {
                resizedSeamToNextUp = PanelResizer.resizeSeamCurve(originalPanel.getSeamToNextUp(), sideShiftMm);
            } else if (mode == ResizeMode.TOP) {
                resizedSeamToNextUp = PanelResizer.resizeSeamCurveTopOnly(originalPanel.getSeamToNextUp(), sideShiftMm, false);
            }
        }
        return resizedSeamToNextUp != null ? resizedSeamToNextUp : originalPanel.getSeamToNextUp();
    }

    public Curve2D getSeamToNextDown() {
        if (resizedSeamToNextDown == null && mode == ResizeMode.GLOBAL) {
            resizedSeamToNextDown = PanelResizer.resizeSeamCurve(originalPanel.getSeamToNextDown(), sideShiftMm);
        }
        return resizedSeamToNextDown != null ? resizedSeamToNextDown : originalPanel.getSeamToNextDown();
    }

    /**
     * Get the original panel (for compatibility).
     */
    public PanelCurves getOriginalPanel() {
        return originalPanel;
    }
}
