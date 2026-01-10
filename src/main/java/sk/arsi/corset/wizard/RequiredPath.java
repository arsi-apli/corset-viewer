package sk.arsi.corset.wizard;

import java.util.Objects;

/**
 * Represents one required SVG path ID in the wizard flow. Previously an enum
 * with 42 values (A..F). Now a dynamic step object.
 */
public final class RequiredPath {

    private final String svgId;

    // wizard assignment state (was previously stored in enum singleton)
    private SvgPathCandidate assignedCandidate;

    public RequiredPath(String svgId) {
        if (svgId == null || svgId.isBlank()) {
            throw new IllegalArgumentException("svgId must be non-empty");
        }
        this.svgId = svgId;
    }

    public String svgId() {
        return svgId;
    }

    public boolean isAssigned() {
        return assignedCandidate != null;
    }

    public SvgPathCandidate getAssignedCandidate() {
        return assignedCandidate;
    }

    public void setAssignedCandidate(SvgPathCandidate candidate) {
        this.assignedCandidate = candidate;
    }

    public void resetAssignment() {
        this.assignedCandidate = null;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof RequiredPath)) {
            return false;
        }
        return Objects.equals(svgId, ((RequiredPath) o).svgId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(svgId);
    }

    @Override
    public String toString() {
        return svgId;
    }
}
