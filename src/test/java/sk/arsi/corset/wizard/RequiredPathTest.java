package sk.arsi.corset.wizard;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RequiredPathTest {

    @Test
    void testStepsReturnsAllRequiredPaths() {
        List<RequiredPath> steps = RequiredPath.steps();
        
        // Should have 42 required paths total:
        // 6 panels × (3 panel curves + 4 seam curves) = 6 × 7 = 42
        assertEquals(42, steps.size(), "Should have 42 required paths");
    }

    @Test
    void testStepsContainsExpectedIds() {
        List<RequiredPath> steps = RequiredPath.steps();
        
        // Check some expected IDs
        assertTrue(steps.contains(RequiredPath.A_TOP));
        assertTrue(steps.contains(RequiredPath.A_WAIST));
        assertTrue(steps.contains(RequiredPath.A_BOTTOM));
        assertTrue(steps.contains(RequiredPath.AA_UP));
        assertTrue(steps.contains(RequiredPath.AB_DOWN));
        assertTrue(steps.contains(RequiredPath.F_TOP));
        assertTrue(steps.contains(RequiredPath.FF_UP));
    }

    @Test
    void testStepsInDeterministicOrder() {
        List<RequiredPath> steps1 = RequiredPath.steps();
        List<RequiredPath> steps2 = RequiredPath.steps();
        
        assertEquals(steps1, steps2, "Steps should be in deterministic order");
    }

    @Test
    void testSvgIdMatchesEnumName() {
        for (RequiredPath path : RequiredPath.values()) {
            assertEquals(path.name(), path.svgId(), "svgId should match enum name");
        }
    }

    @Test
    void testAssignmentOperations() {
        RequiredPath path = RequiredPath.A_TOP;
        
        // Initially not assigned
        assertFalse(path.isAssigned());
        assertNull(path.getAssignedCandidate());
        
        // Create a mock candidate
        SvgPathCandidate candidate = new SvgPathCandidate(
            0, "test", "M 0 0 L 10 10", false, List.of()
        );
        
        // Assign
        path.setAssignedCandidate(candidate);
        assertTrue(path.isAssigned());
        assertEquals(candidate, path.getAssignedCandidate());
        
        // Reset
        path.resetAssignment();
        assertFalse(path.isAssigned());
        assertNull(path.getAssignedCandidate());
    }

    @Test
    void testResetAllAssignments() {
        // Assign some paths
        RequiredPath.A_TOP.setAssignedCandidate(
            new SvgPathCandidate(0, "test1", "M 0 0", false, List.of())
        );
        RequiredPath.B_WAIST.setAssignedCandidate(
            new SvgPathCandidate(1, "test2", "M 0 0", false, List.of())
        );
        
        assertTrue(RequiredPath.A_TOP.isAssigned());
        assertTrue(RequiredPath.B_WAIST.isAssigned());
        
        // Reset all
        RequiredPath.resetAllAssignments();
        
        assertFalse(RequiredPath.A_TOP.isAssigned());
        assertFalse(RequiredPath.B_WAIST.isAssigned());
    }
}
