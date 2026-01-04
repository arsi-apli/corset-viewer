package sk.arsi.corset.export;

import sk.arsi.corset.model.Pt;

/**
 * Represents a single notch (tick mark) on a seam.
 */
public final class Notch {
    private final Pt start;
    private final Pt end;
    private final String id;

    public Notch(Pt start, Pt end, String id) {
        this.start = start;
        this.end = end;
        this.id = id;
    }

    public Pt getStart() {
        return start;
    }

    public Pt getEnd() {
        return end;
    }

    public String getId() {
        return id;
    }
}
