package sk.arsi.corset.wizard;

import sk.arsi.corset.model.Pt;

import java.util.List;

/**
 * Represents a candidate path element from the SVG.
 */
public final class SvgPathCandidate {
    
    private final int index;
    private final String originalId;
    private final String dAttribute;
    private final boolean originallyRequiredId;
    private final List<Pt> polyline;
    
    private RequiredPath assignedRequired;

    public SvgPathCandidate(int index, String originalId, String dAttribute, 
                           boolean originallyRequiredId, List<Pt> polyline) {
        this.index = index;
        this.originalId = originalId;
        this.dAttribute = dAttribute;
        this.originallyRequiredId = originallyRequiredId;
        this.polyline = polyline;
    }

    public int getIndex() {
        return index;
    }

    public String getOriginalId() {
        return originalId;
    }

    public String getDAttribute() {
        return dAttribute;
    }

    public boolean isOriginallyRequiredId() {
        return originallyRequiredId;
    }

    public List<Pt> getPolyline() {
        return polyline;
    }

    public RequiredPath getAssignedRequired() {
        return assignedRequired;
    }

    void setAssignedRequired(RequiredPath required) {
        this.assignedRequired = required;
    }

    /**
     * Check if this candidate has been assigned a required ID.
     */
    public boolean isAssigned() {
        return assignedRequired != null;
    }

    /**
     * Check if this candidate should be shown as green (already has required ID or has been assigned).
     */
    public boolean isGreen() {
        return originallyRequiredId || assignedRequired != null;
    }
}
