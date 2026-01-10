package sk.arsi.corset.svg;

import sk.arsi.corset.model.PanelId;

/**
 * Generates SVG IDs according to the naming contract. Now supports a variable
 * panel range A..maxPanel.
 */
public final class PatternContract {

    private final char maxPanel; // 'F','G','H',...

    public PatternContract(char maxPanel) {
        char up = Character.toUpperCase(maxPanel);
        if (up < 'A' || up > 'Z') {
            throw new IllegalArgumentException("maxPanel must be A..Z, got: " + maxPanel);
        }
        this.maxPanel = up;
    }

    /**
     * Backward-compatible default: A..F
     */
    public PatternContract() {
        this('F');
    }

    public char getMaxPanel() {
        return maxPanel;
    }

    public String topId(PanelId panelId) {
        return panelId.name() + "_TOP";
    }

    public String bottomId(PanelId panelId) {
        return panelId.name() + "_BOTTOM";
    }

    public String waistId(PanelId panelId) {
        return panelId.name() + "_WAIST";
    }

    /**
     * e.g. for panel C: "CB"; for panel A: "AA"
     */
    public String seamToPrevId(PanelId panelId) {
        if (panelId.letter() == 'A') {
            return "AA";
        }
        PanelId prev = panelId.prev();
        if (prev == null) {
            throw new IllegalStateException("prev is null for " + panelId);
        }
        return panelId.name() + prev.name();
    }

    /**
     * e.g. for panel C: "CD"; for last panel H: "HH"
     */
    public String seamToNextId(PanelId panelId) {
        if (panelId.letter() == maxPanel) {
            return "" + maxPanel + maxPanel;
        }
        PanelId next = panelId.next();
        if (next == null) {
            throw new IllegalStateException("next is null for " + panelId);
        }
        return panelId.name() + next.name();
    }

    public String seamUpId(String seam) {
        return seam + "_UP";
    }

    public String seamDownId(String seam) {
        return seam + "_DOWN";
    }
}
