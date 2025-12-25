package sk.arsi.corset.measure;

import sk.arsi.corset.model.PanelId;

/**
 * Data structure to hold seam measurement results for a seam pair (e.g., AB).
 * Used to share measurement data between Measurements tab and Canvas2DView.
 */
public final class SeamMeasurementData {

    private final String seamName;
    private final PanelId leftPanel;
    private final PanelId rightPanel;
    
    // TOP measurements (above waist)
    private final double leftUpTop;
    private final double rightUpTop;
    private final double diffUpTop;
    private final double leftDownTop;
    private final double rightDownTop;
    private final double diffDownTop;
    
    // BOTTOM measurements (below waist)
    private final double leftUpBottom;
    private final double rightUpBottom;
    private final double diffUpBottom;
    private final double leftDownBottom;
    private final double rightDownBottom;
    private final double diffDownBottom;

    public SeamMeasurementData(
            String seamName,
            PanelId leftPanel,
            PanelId rightPanel,
            double leftUpTop,
            double rightUpTop,
            double diffUpTop,
            double leftDownTop,
            double rightDownTop,
            double diffDownTop,
            double leftUpBottom,
            double rightUpBottom,
            double diffUpBottom,
            double leftDownBottom,
            double rightDownBottom,
            double diffDownBottom) {
        this.seamName = seamName;
        this.leftPanel = leftPanel;
        this.rightPanel = rightPanel;
        this.leftUpTop = leftUpTop;
        this.rightUpTop = rightUpTop;
        this.diffUpTop = diffUpTop;
        this.leftDownTop = leftDownTop;
        this.rightDownTop = rightDownTop;
        this.diffDownTop = diffDownTop;
        this.leftUpBottom = leftUpBottom;
        this.rightUpBottom = rightUpBottom;
        this.diffUpBottom = diffUpBottom;
        this.leftDownBottom = leftDownBottom;
        this.rightDownBottom = rightDownBottom;
        this.diffDownBottom = diffDownBottom;
    }

    public String getSeamName() {
        return seamName;
    }

    public PanelId getLeftPanel() {
        return leftPanel;
    }

    public PanelId getRightPanel() {
        return rightPanel;
    }

    public double getLeftUpTop() {
        return leftUpTop;
    }

    public double getRightUpTop() {
        return rightUpTop;
    }

    public double getDiffUpTop() {
        return diffUpTop;
    }

    public double getLeftDownTop() {
        return leftDownTop;
    }

    public double getRightDownTop() {
        return rightDownTop;
    }

    public double getDiffDownTop() {
        return diffDownTop;
    }

    public double getLeftUpBottom() {
        return leftUpBottom;
    }

    public double getRightUpBottom() {
        return rightUpBottom;
    }

    public double getDiffUpBottom() {
        return diffUpBottom;
    }

    public double getLeftDownBottom() {
        return leftDownBottom;
    }

    public double getRightDownBottom() {
        return rightDownBottom;
    }

    public double getDiffDownBottom() {
        return diffDownBottom;
    }
    
    /**
     * Check if TOP diff exceeds tolerance for either UP or DOWN curves.
     */
    public boolean topExceedsTolerance(double tolerance) {
        return Math.abs(diffUpTop) > tolerance || Math.abs(diffDownTop) > tolerance;
    }
    
    /**
     * Check if BOTTOM diff exceeds tolerance for either UP or DOWN curves.
     */
    public boolean bottomExceedsTolerance(double tolerance) {
        return Math.abs(diffUpBottom) > tolerance || Math.abs(diffDownBottom) > tolerance;
    }
}
