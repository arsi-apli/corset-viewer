package sk.arsi.corset.svg;

import sk.arsi.corset.model.PanelId;

public final class PatternContract {

    public String topId(PanelId panelId) {
        return panelId.name() + "_TOP";
    }

    public String bottomId(PanelId panelId) {
        return panelId.name() + "_BOTTOM";
    }

    public String waistId(PanelId panelId) {
        return panelId.name() + "_WAIST";
    }

    public String seamToPrevId(PanelId panelId) {
        if (panelId == PanelId.A) {
            return "AA"; // busk side for A
        }
        if (panelId == PanelId.B) {
            return "BA";
        }
        if (panelId == PanelId.C) {
            return "CB";
        }
        if (panelId == PanelId.D) {
            return "DC";
        }
        if (panelId == PanelId.E) {
            return "ED";
        }
        if (panelId == PanelId.F) {
            return "FE";
        }
        throw new IllegalArgumentException("Unknown panel: " + panelId);
    }

    public String seamToNextId(PanelId panelId) {
        if (panelId == PanelId.A) {
            return "AB";
        }
        if (panelId == PanelId.B) {
            return "BC";
        }
        if (panelId == PanelId.C) {
            return "CD";
        }
        if (panelId == PanelId.D) {
            return "DE";
        }
        if (panelId == PanelId.E) {
            return "EF";
        }
        if (panelId == PanelId.F) {
            return "FF"; // lacing side for F
        }
        throw new IllegalArgumentException("Unknown panel: " + panelId);
    }

    public String seamUpId(String seam) {
        return seam + "_UP";
    }

    public String seamDownId(String seam) {
        return seam + "_DOWN";
    }
}
