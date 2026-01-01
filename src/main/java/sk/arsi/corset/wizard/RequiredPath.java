package sk.arsi.corset.wizard;

import sk.arsi.corset.model.PanelId;
import sk.arsi.corset.svg.PatternContract;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents all required path IDs in deterministic wizard order.
 */
public enum RequiredPath {
    A_TOP, A_WAIST, A_BOTTOM,
    AA_UP, AA_DOWN, AB_UP, AB_DOWN,
    
    B_TOP, B_WAIST, B_BOTTOM,
    BA_UP, BA_DOWN, BC_UP, BC_DOWN,
    
    C_TOP, C_WAIST, C_BOTTOM,
    CB_UP, CB_DOWN, CD_UP, CD_DOWN,
    
    D_TOP, D_WAIST, D_BOTTOM,
    DC_UP, DC_DOWN, DE_UP, DE_DOWN,
    
    E_TOP, E_WAIST, E_BOTTOM,
    ED_UP, ED_DOWN, EF_UP, EF_DOWN,
    
    F_TOP, F_WAIST, F_BOTTOM,
    FE_UP, FE_DOWN, FF_UP, FF_DOWN;

    private SvgPathCandidate assignedCandidate;

    /**
     * Get the SVG id string for this required path.
     */
    public String svgId() {
        return name();
    }

    /**
     * Get the assigned candidate, or null if not yet assigned.
     */
    public SvgPathCandidate getAssignedCandidate() {
        return assignedCandidate;
    }

    /**
     * Set the assigned candidate.
     */
    void setAssignedCandidate(SvgPathCandidate candidate) {
        this.assignedCandidate = candidate;
    }

    /**
     * Reset assignment.
     */
    public void resetAssignment() {
        this.assignedCandidate = null;
    }

    /**
     * Check if this required path has been assigned.
     */
    public boolean isAssigned() {
        return assignedCandidate != null;
    }

    /**
     * Get all required paths in deterministic wizard order.
     */
    public static List<RequiredPath> steps() {
        List<RequiredPath> result = new ArrayList<>();
        PatternContract contract = new PatternContract();
        PanelId[] panels = new PanelId[]{PanelId.A, PanelId.B, PanelId.C, PanelId.D, PanelId.E, PanelId.F};
        
        for (PanelId panelId : panels) {
            // Add panel curves
            result.add(valueOf(contract.topId(panelId)));
            result.add(valueOf(contract.waistId(panelId)));
            result.add(valueOf(contract.bottomId(panelId)));
            
            // Add seam curves
            String seamToPrevId = contract.seamToPrevId(panelId);
            String seamToNextId = contract.seamToNextId(panelId);
            
            result.add(valueOf(contract.seamUpId(seamToPrevId)));
            result.add(valueOf(contract.seamDownId(seamToPrevId)));
            result.add(valueOf(contract.seamUpId(seamToNextId)));
            result.add(valueOf(contract.seamDownId(seamToNextId)));
        }
        
        return result;
    }

    /**
     * Reset all assignments.
     */
    public static void resetAllAssignments() {
        for (RequiredPath path : values()) {
            path.resetAssignment();
        }
    }
}
