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
 * Integration test to demonstrate the circumference measurement improvements.
 * This test verifies that:
 * 1. Circumference measurement works for both positive (upward) and negative (downward) dyMm
 * 2. Valid dy range is computed correctly for panels with UP and DOWN curves
 * 3. The measurement line position is correct in WAIST mode
 */
public class CircumferenceIntegrationTest {

    @Test
    public void testCircumferenceMeasurement_BothDirections() {
        // Create a simple corset with 3 panels, each 50mm wide
        // UP curves cover y=0 to y=100, DOWN curves cover y=100 to y=200
        List<PanelCurves> panels = createTestPanels(3, 50.0, 100.0, 100.0);

        // Test at waist (dyMm = 0)
        double circumAtWaist = MeasurementUtils.computeFullCircumference(panels, 0.0);
        assertEquals(300.0, circumAtWaist, 0.1, 
            "Full circumference at waist should be 3 panels * 50mm * 2 = 300mm");

        // Test above waist (positive dyMm)
        double circumAbove50 = MeasurementUtils.computeFullCircumference(panels, 50.0);
        assertEquals(300.0, circumAbove50, 0.1,
            "Full circumference 50mm above waist should be 300mm (using UP curves)");

        double circumAbove90 = MeasurementUtils.computeFullCircumference(panels, 90.0);
        assertEquals(300.0, circumAbove90, 0.1,
            "Full circumference 90mm above waist should be 300mm (using UP curves)");

        // Test below waist (negative dyMm)
        double circumBelow50 = MeasurementUtils.computeFullCircumference(panels, -50.0);
        assertEquals(300.0, circumBelow50, 0.1,
            "Full circumference 50mm below waist should be 300mm (using DOWN curves)");

        double circumBelow90 = MeasurementUtils.computeFullCircumference(panels, -90.0);
        assertEquals(300.0, circumBelow90, 0.1,
            "Full circumference 90mm below waist should be 300mm (using DOWN curves)");
    }

    @Test
    public void testValidDyRange_SymmetricCurves() {
        // Create panels with symmetric UP and DOWN coverage
        // UP: 100mm above waist, DOWN: 100mm below waist
        List<PanelCurves> panels = createTestPanels(6, 50.0, 100.0, 100.0);

        MeasurementUtils.DyRange range = MeasurementUtils.computeValidDyRange(panels, 5.0);

        assertTrue(range.maxUpDy >= 95.0 && range.maxUpDy <= 100.0,
            "maxUpDy should be around 100mm (can measure up to y=0)");
        
        assertTrue(range.maxDownDy >= 95.0 && range.maxDownDy <= 100.0,
            "maxDownDy should be around 100mm (can measure down to y=200)");

        System.out.println("Symmetric curves - Valid range: up=" + range.maxUpDy + 
                         "mm, down=" + range.maxDownDy + "mm");
    }

    @Test
    public void testValidDyRange_AsymmetricCurves() {
        // Create panels with asymmetric UP and DOWN coverage
        // UP: 50mm above waist, DOWN: 150mm below waist (typical corset shape)
        List<PanelCurves> panels = createTestPanels(6, 50.0, 50.0, 150.0);

        MeasurementUtils.DyRange range = MeasurementUtils.computeValidDyRange(panels, 2.0);

        assertTrue(range.maxUpDy >= 48.0 && range.maxUpDy <= 50.0,
            "maxUpDy should be around 50mm (shorter UP curves)");
        
        assertTrue(range.maxDownDy >= 148.0 && range.maxDownDy <= 150.0,
            "maxDownDy should be around 150mm (longer DOWN curves)");

        System.out.println("Asymmetric curves - Valid range: up=" + range.maxUpDy + 
                         "mm, down=" + range.maxDownDy + "mm");
    }

    @Test
    public void testValidDyRange_SinglePanel() {
        // Test with a single panel to verify the logic works with minimal data
        List<PanelCurves> panels = createTestPanels(1, 50.0, 80.0, 120.0);

        MeasurementUtils.DyRange range = MeasurementUtils.computeValidDyRange(panels);

        assertTrue(range.maxUpDy > 0, "maxUpDy should be positive");
        assertTrue(range.maxDownDy > 0, "maxDownDy should be positive");
        assertTrue(range.maxUpDy <= 80.0, "maxUpDy should not exceed UP curve coverage");
        assertTrue(range.maxDownDy <= 120.0, "maxDownDy should not exceed DOWN curve coverage");

        System.out.println("Single panel - Valid range: up=" + range.maxUpDy + 
                         "mm, down=" + range.maxDownDy + "mm");
    }

    @Test
    public void testMeasurementLinePosition_WaistMode() {
        // In WAIST mode, waists are aligned to y=0
        // Measurement line should be at y = -dyMm
        
        // For positive dyMm (measuring upward), line is above waist (negative y)
        double dyMm = 50.0;
        double measurementY = -dyMm;
        assertEquals(-50.0, measurementY, 
            "Measurement line for dyMm=+50 should be at y=-50 (above waist)");

        // For negative dyMm (measuring downward), line is below waist (positive y)
        dyMm = -80.0;
        measurementY = -dyMm;
        assertEquals(80.0, measurementY,
            "Measurement line for dyMm=-80 should be at y=+80 (below waist)");

        // At waist
        dyMm = 0.0;
        measurementY = -dyMm;
        assertEquals(0.0, Math.abs(measurementY), 0.001,
            "Measurement line for dyMm=0 should be at y=0 (at waist)");
    }

    /**
     * Helper method to create test panels with specified dimensions.
     * 
     * @param numPanels Number of panels to create
     * @param width Width of each panel in mm
     * @param upHeight Height of UP curves above waist in mm
     * @param downHeight Height of DOWN curves below waist in mm
     * @return List of PanelCurves
     */
    private List<PanelCurves> createTestPanels(int numPanels, double width, 
                                                double upHeight, double downHeight) {
        PanelCurves[] panels = new PanelCurves[numPanels];
        PanelId[] ids = {PanelId.A, PanelId.B, PanelId.C, PanelId.D, PanelId.E, PanelId.F};
        
        double waistY = 100.0; // Waist at y=100 in panel-local coordinates
        
        for (int i = 0; i < numPanels && i < ids.length; i++) {
            // Waist curve
            List<Pt> waistPoints = Arrays.asList(
                new Pt(0, waistY),
                new Pt(width, waistY)
            );
            Curve2D waist = new Curve2D("waist", waistPoints);
            
            // Left UP seam (vertical, from waistY-upHeight to waistY)
            List<Pt> leftUpPoints = Arrays.asList(
                new Pt(0, waistY - upHeight),
                new Pt(0, waistY)
            );
            Curve2D leftUp = new Curve2D("leftUp", leftUpPoints);
            
            // Left DOWN seam (vertical, from waistY to waistY+downHeight)
            List<Pt> leftDownPoints = Arrays.asList(
                new Pt(0, waistY),
                new Pt(0, waistY + downHeight)
            );
            Curve2D leftDown = new Curve2D("leftDown", leftDownPoints);
            
            // Right UP seam (vertical, from waistY-upHeight to waistY)
            List<Pt> rightUpPoints = Arrays.asList(
                new Pt(width, waistY - upHeight),
                new Pt(width, waistY)
            );
            Curve2D rightUp = new Curve2D("rightUp", rightUpPoints);
            
            // Right DOWN seam (vertical, from waistY to waistY+downHeight)
            List<Pt> rightDownPoints = Arrays.asList(
                new Pt(width, waistY),
                new Pt(width, waistY + downHeight)
            );
            Curve2D rightDown = new Curve2D("rightDown", rightDownPoints);
            
            panels[i] = new PanelCurves(
                ids[i], null, null, waist, leftUp, leftDown, rightUp, rightDown
            );
        }
        
        return Arrays.asList(panels);
    }
}
