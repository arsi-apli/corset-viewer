package sk.arsi.corset.model;

import java.util.List;

public final class PanelTopology {

    private final List<Character> order;

    public PanelTopology(List<Character> order) {
        this.order = order;
    }

    public String leftSeamBase(char panel) {
        int idx = indexOf(panel);
        if (idx == 0) {
            return "" + panel + panel; // AA
        }
        char prev = order.get(idx - 1);
        return "" + panel + prev; // BA, CB, DC...
    }

    public String rightSeamBase(char panel) {
        int idx = indexOf(panel);
        if (idx == order.size() - 1) {
            return "" + panel + panel; // FF
        }
        char next = order.get(idx + 1);
        return "" + panel + next; // AB, BC, CD...
    }

    public String waistId(char panel) {
        return panel + "_WAIST";
    }

    public String topId(char panel) {
        return panel + "_TOP";
    }

    public String bottomId(char panel) {
        return panel + "_BOTTOM";
    }

    public String seamUpId(String seamBase) {
        return seamBase + "_UP";
    }

    public String seamDownId(String seamBase) {
        return seamBase + "_DOWN";
    }

    private int indexOf(char panel) {
        for (int i = 0; i < order.size(); i++) {
            if (order.get(i).charValue() == panel) {
                return i;
            }
        }
        throw new IllegalArgumentException("Panel not in order: " + panel);
    }
}
