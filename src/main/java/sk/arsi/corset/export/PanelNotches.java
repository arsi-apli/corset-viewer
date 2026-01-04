package sk.arsi.corset.export;

import sk.arsi.corset.model.PanelId;

import java.util.List;

/**
 * Represents all notches for a single panel.
 */
final class PanelNotches {
    private final PanelId panelId;
    private final List<Notch> notches;

    public PanelNotches(PanelId panelId, List<Notch> notches) {
        this.panelId = panelId;
        this.notches = notches;
    }

    public PanelId getPanelId() {
        return panelId;
    }

    public List<Notch> getNotches() {
        return notches;
    }
}
