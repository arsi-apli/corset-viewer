package sk.arsi.corset.model;

public final class PanelCurves {

    private final PanelId panelId;

    private final Curve2D top;
    private final Curve2D bottom;
    private final Curve2D waist;

    private final Curve2D seamToPrevUp;
    private final Curve2D seamToPrevDown;
    private final Curve2D seamToNextUp;
    private final Curve2D seamToNextDown;

    public PanelCurves(
            PanelId panelId,
            Curve2D top,
            Curve2D bottom,
            Curve2D waist,
            Curve2D seamToPrevUp,
            Curve2D seamToPrevDown,
            Curve2D seamToNextUp,
            Curve2D seamToNextDown
    ) {
        this.panelId = panelId;
        this.top = top;
        this.bottom = bottom;
        this.waist = waist;
        this.seamToPrevUp = seamToPrevUp;
        this.seamToPrevDown = seamToPrevDown;
        this.seamToNextUp = seamToNextUp;
        this.seamToNextDown = seamToNextDown;
    }

    public PanelId getPanelId() {
        return panelId;
    }

    public Curve2D getTop() {
        return top;
    }

    public Curve2D getBottom() {
        return bottom;
    }

    public Curve2D getWaist() {
        return waist;
    }

    public Curve2D getSeamToPrevUp() {
        return seamToPrevUp;
    }

    public Curve2D getSeamToPrevDown() {
        return seamToPrevDown;
    }

    public Curve2D getSeamToNextUp() {
        return seamToNextUp;
    }

    public Curve2D getSeamToNextDown() {
        return seamToNextDown;
    }
}
