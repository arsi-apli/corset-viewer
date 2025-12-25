package sk.arsi.corset.measure;

import org.junit.jupiter.api.Test;
import sk.arsi.corset.model.Curve2D;
import sk.arsi.corset.model.PanelCurves;
import sk.arsi.corset.model.PanelId;
import sk.arsi.corset.model.Pt;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SeamMeasurementService.
 */
public class SeamMeasurementServiceTest {

    @Test
    public void testComputeAllSeamMeasurements_EmptyList() {
        List<SeamMeasurementData> result = SeamMeasurementService.computeAllSeamMeasurements(null);
        assertNotNull(result);
        assertTrue(result.isEmpty());

        result = SeamMeasurementService.computeAllSeamMeasurements(Arrays.asList());
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testComputeAllSeamMeasurements_TwoPanels() {
        // Create two simple panels A and B
        PanelCurves panelA = createSimplePanel(PanelId.A, 100.0);
        PanelCurves panelB = createSimplePanel(PanelId.B, 100.0);

        List<PanelCurves> panels = Arrays.asList(panelA, panelB);
        List<SeamMeasurementData> result = SeamMeasurementService.computeAllSeamMeasurements(panels);

        // Service always returns 5 seam pairs (AB, BC, CD, DE, EF) 
        // even if some panels are missing
        assertEquals(5, result.size(), "Should have 5 seam pairs");

        SeamMeasurementData ab = result.get(0);
        assertEquals("AB", ab.getSeamName());
        assertEquals(PanelId.A, ab.getLeftPanel());
        assertEquals(PanelId.B, ab.getRightPanel());
        
        // Both panels have same waist at 100, so seams should be equal
        // Each vertical seam from 0 to 100 (above) and 100 to 200 (below) = 100mm each
        assertEquals(100.0, ab.getLeftUpTop(), 0.1);
        assertEquals(100.0, ab.getRightUpTop(), 0.1);
        assertEquals(0.0, ab.getDiffUpTop(), 0.1);
        
        assertEquals(100.0, ab.getLeftUpBottom(), 0.1);
        assertEquals(100.0, ab.getRightUpBottom(), 0.1);
        assertEquals(0.0, ab.getDiffUpBottom(), 0.1);
        
        // BC should have zero measurements (B is present but C is missing)
        SeamMeasurementData bc = result.get(1);
        assertEquals("BC", bc.getSeamName());
        assertEquals(0.0, bc.getLeftUpTop(), 0.1);
        assertEquals(0.0, bc.getRightUpTop(), 0.1);
    }

    @Test
    public void testComputeAllSeamMeasurements_AllSixPanels() {
        // Create all six panels
        List<PanelCurves> panels = Arrays.asList(
                createSimplePanel(PanelId.A, 100.0),
                createSimplePanel(PanelId.B, 100.0),
                createSimplePanel(PanelId.C, 100.0),
                createSimplePanel(PanelId.D, 100.0),
                createSimplePanel(PanelId.E, 100.0),
                createSimplePanel(PanelId.F, 100.0)
        );

        List<SeamMeasurementData> result = SeamMeasurementService.computeAllSeamMeasurements(panels);

        assertEquals(5, result.size(), "Should have 5 seam pairs (AB, BC, CD, DE, EF)");

        String[] expectedNames = {"AB", "BC", "CD", "DE", "EF"};
        for (int i = 0; i < 5; i++) {
            assertEquals(expectedNames[i], result.get(i).getSeamName());
        }
    }

    @Test
    public void testSeamMeasurementData_ToleranceChecks() {
        SeamMeasurementData data = new SeamMeasurementData(
                "AB", PanelId.A, PanelId.B,
                100.0, 99.0, 1.0,  // TOP: diff = 1.0
                100.0, 98.5, 1.5,  // TOP DOWN: diff = 1.5
                100.0, 99.2, 0.8,  // BOTTOM: diff = 0.8
                100.0, 99.8, 0.2   // BOTTOM DOWN: diff = 0.2
        );

        // With tolerance 0.5mm
        assertTrue(data.topExceedsTolerance(0.5), "TOP should exceed 0.5mm tolerance");
        assertTrue(data.bottomExceedsTolerance(0.5), "BOTTOM should exceed 0.5mm tolerance");

        // With tolerance 2.0mm
        assertFalse(data.topExceedsTolerance(2.0), "TOP should not exceed 2.0mm tolerance");
        assertFalse(data.bottomExceedsTolerance(2.0), "BOTTOM should not exceed 2.0mm tolerance");

        // With tolerance 1.0mm - edge case
        assertTrue(data.topExceedsTolerance(1.0), "TOP should exceed 1.0mm tolerance (DOWN diff = 1.5)");
        assertFalse(data.bottomExceedsTolerance(1.0), "BOTTOM should not exceed 1.0mm tolerance");
    }

    @Test
    public void testSeamMeasurementData_NegativeDifferences() {
        SeamMeasurementData data = new SeamMeasurementData(
                "AB", PanelId.A, PanelId.B,
                98.0, 100.0, -2.0,  // TOP: diff = -2.0
                100.0, 100.0, 0.0,  // TOP DOWN: diff = 0.0
                99.0, 100.0, -1.0,  // BOTTOM: diff = -1.0
                100.0, 100.0, 0.0   // BOTTOM DOWN: diff = 0.0
        );

        // Tolerance checks use absolute value
        assertTrue(data.topExceedsTolerance(1.0), "TOP should exceed 1.0mm tolerance (|diff| = 2.0)");
        assertTrue(data.bottomExceedsTolerance(0.5), "BOTTOM should exceed 0.5mm tolerance (|diff| = 1.0)");
        
        assertFalse(data.topExceedsTolerance(2.0), "TOP should not exceed 2.0mm tolerance");
        assertFalse(data.bottomExceedsTolerance(1.0), "BOTTOM should not exceed 1.0mm tolerance");
    }

    /**
     * Helper to create a simple rectangular panel for testing.
     * Waist at waistY, vertical seams from y=0 to y=200.
     */
    private PanelCurves createSimplePanel(PanelId id, double waistY) {
        List<Pt> waistPoints = Arrays.asList(
                new Pt(0, waistY),
                new Pt(50, waistY)
        );
        Curve2D waist = new Curve2D("waist", waistPoints);

        // Left seam (vertical line at x=0)
        List<Pt> leftPoints = Arrays.asList(
                new Pt(0, 0),
                new Pt(0, 200)
        );
        Curve2D leftSeam = new Curve2D("left", leftPoints);

        // Right seam (vertical line at x=50)
        List<Pt> rightPoints = Arrays.asList(
                new Pt(50, 0),
                new Pt(50, 200)
        );
        Curve2D rightSeam = new Curve2D("right", rightPoints);

        return new PanelCurves(
                id,
                null, // top
                null, // bottom
                waist,
                leftSeam, // seamToPrevUp
                leftSeam, // seamToPrevDown (same as Up for simplicity)
                rightSeam, // seamToNextUp
                rightSeam  // seamToNextDown (same as Up for simplicity)
        );
    }
}
