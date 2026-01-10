package sk.arsi.corset.measure;

import org.junit.jupiter.api.Test;
import sk.arsi.corset.model.Curve2D;
import sk.arsi.corset.model.PanelCurves;
import sk.arsi.corset.model.PanelId;
import sk.arsi.corset.model.Pt;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MeasurementUtils, focusing on the circumference dead-zone feature.
 */
class MeasurementUtilsTest {

    /**
     * Create a simple test panel with predictable waist and seam curves.
     * This creates a rectangular panel 100mm wide and 200mm tall, centered at y=0.
     */
    private PanelCurves createTestPanel() {
        // Waist curve: horizontal line at y=0, from x=0 to x=100
        List<Pt> waistPoints = new ArrayList<>();
        waistPoints.add(new Pt(0, 0));
        waistPoints.add(new Pt(100, 0));
        Curve2D waist = new Curve2D("test-waist", waistPoints);

        // Left seam (seamToPrev): vertical line at x=0, from y=-100 to y=100
        List<Pt> leftPoints = new ArrayList<>();
        leftPoints.add(new Pt(0, -100));
        leftPoints.add(new Pt(0, 100));
        Curve2D leftSeamUp = new Curve2D("test-left-up", leftPoints);
        Curve2D leftSeamDown = new Curve2D("test-left-down", new ArrayList<>(leftPoints));

        // Right seam (seamToNext): vertical line at x=100, from y=-100 to y=100
        List<Pt> rightPoints = new ArrayList<>();
        rightPoints.add(new Pt(100, -100));
        rightPoints.add(new Pt(100, 100));
        Curve2D rightSeamUp = new Curve2D("test-right-up", rightPoints);
        Curve2D rightSeamDown = new Curve2D("test-right-down", new ArrayList<>(rightPoints));

        // Top and bottom curves (required by PanelCurves constructor)
        List<Pt> topPoints = new ArrayList<>();
        topPoints.add(new Pt(0, 100));
        topPoints.add(new Pt(100, 100));
        Curve2D top = new Curve2D("test-top", topPoints);

        List<Pt> bottomPoints = new ArrayList<>();
        bottomPoints.add(new Pt(0, -100));
        bottomPoints.add(new Pt(100, -100));
        Curve2D bottom = new Curve2D("test-bottom", bottomPoints);

        return new PanelCurves(
                PanelId.of('A'),
                top,
                bottom,
                waist,
                leftSeamUp,
                leftSeamDown,
                rightSeamUp,
                rightSeamDown
        );
    }

    @Test
    void testComputeFullCircumference_AtExactWaist() {
        // Test that dyMm = 0 uses waist circumference
        List<PanelCurves> panels = new ArrayList<>();
        panels.add(createTestPanel());

        double result = MeasurementUtils.computeFullCircumference(panels, 0.0);

        // Expected: 2 * waist curve length = 2 * 100 = 200
        double waistLength = MeasurementUtils.computeFullWaistCircumference(panels);
        assertEquals(200.0, waistLength, 0.001, "Waist circumference should be 200mm");
        assertEquals(waistLength, result, 0.001, "At dyMm=0, should use waist circumference");
    }

    @Test
    void testComputeFullCircumference_WithinDeadZonePositive() {
        // Test that dyMm = 4.9 uses waist circumference (within dead-zone)
        List<PanelCurves> panels = new ArrayList<>();
        panels.add(createTestPanel());

        double result = MeasurementUtils.computeFullCircumference(panels, 4.9);
        double waistCircumference = MeasurementUtils.computeFullWaistCircumference(panels);

        assertEquals(waistCircumference, result, 0.001,
                "At dyMm=4.9 (within dead-zone), should use waist circumference");
    }

    @Test
    void testComputeFullCircumference_WithinDeadZoneNegative() {
        // Test that dyMm = -4.9 uses waist circumference (within dead-zone)
        List<PanelCurves> panels = new ArrayList<>();
        panels.add(createTestPanel());

        double result = MeasurementUtils.computeFullCircumference(panels, -4.9);
        double waistCircumference = MeasurementUtils.computeFullWaistCircumference(panels);

        assertEquals(waistCircumference, result, 0.001,
                "At dyMm=-4.9 (within dead-zone), should use waist circumference");
    }

    @Test
    void testComputeFullCircumference_AtDeadZoneBoundaryPositive() {
        // Test that dyMm = 5.0 uses intersection-based circumference (outside dead-zone)
        List<PanelCurves> panels = new ArrayList<>();
        panels.add(createTestPanel());

        double result = MeasurementUtils.computeFullCircumference(panels, 5.0);
        double waistCircumference = MeasurementUtils.computeFullWaistCircumference(panels);
        double intersectionCircumference = 2.0 * MeasurementUtils.computeHalfCircumference(panels, 5.0);

        // At 5.0mm, should use intersection-based calculation
        // For our test panel (rectangular), this should still be 200mm
        assertEquals(intersectionCircumference, result, 0.001,
                "At dyMm=5.0 (at boundary), should use intersection-based circumference");
        assertEquals(200.0, result, 0.001,
                "For rectangular panel, circumference should be constant at 200mm");
    }

    @Test
    void testComputeFullCircumference_AtDeadZoneBoundaryNegative() {
        // Test that dyMm = -5.0 uses intersection-based circumference (outside dead-zone)
        List<PanelCurves> panels = new ArrayList<>();
        panels.add(createTestPanel());

        double result = MeasurementUtils.computeFullCircumference(panels, -5.0);
        double intersectionCircumference = 2.0 * MeasurementUtils.computeHalfCircumference(panels, -5.0);

        assertEquals(intersectionCircumference, result, 0.001,
                "At dyMm=-5.0 (at boundary), should use intersection-based circumference");
    }

    @Test
    void testComputeFullCircumference_FarAboveWaist() {
        // Test that dyMm = 50 uses intersection-based circumference
        List<PanelCurves> panels = new ArrayList<>();
        panels.add(createTestPanel());

        double result = MeasurementUtils.computeFullCircumference(panels, 50.0);
        double intersectionCircumference = 2.0 * MeasurementUtils.computeHalfCircumference(panels, 50.0);

        assertEquals(intersectionCircumference, result, 0.001,
                "At dyMm=50 (far from waist), should use intersection-based circumference");
    }

    @Test
    void testComputeFullCircumference_FarBelowWaist() {
        // Test that dyMm = -50 uses intersection-based circumference
        List<PanelCurves> panels = new ArrayList<>();
        panels.add(createTestPanel());

        double result = MeasurementUtils.computeFullCircumference(panels, -50.0);
        double intersectionCircumference = 2.0 * MeasurementUtils.computeHalfCircumference(panels, -50.0);

        assertEquals(intersectionCircumference, result, 0.001,
                "At dyMm=-50 (far from waist), should use intersection-based circumference");
    }

    @Test
    void testComputeFullCircumference_MultiplePanel() {
        // Test with multiple panels to ensure the dead-zone works correctly
        List<PanelCurves> panels = new ArrayList<>();
        panels.add(createTestPanel());
        panels.add(createTestPanel());

        double resultAtWaist = MeasurementUtils.computeFullCircumference(panels, 0.0);
        double resultInDeadZone = MeasurementUtils.computeFullCircumference(panels, 4.9);
        double resultOutsideDeadZone = MeasurementUtils.computeFullCircumference(panels, 5.0);

        // Within dead-zone, all should use waist circumference
        assertEquals(resultAtWaist, resultInDeadZone, 0.001,
                "Results within dead-zone should be consistent");

        // Expected: 2 panels * 2 * 100mm = 400mm
        assertEquals(400.0, resultAtWaist, 0.001, "Total waist circumference for 2 panels");
    }

    @Test
    void testDeadZoneBoundary_Precision() {
        // Test values very close to the boundary to ensure correct behavior
        List<PanelCurves> panels = new ArrayList<>();
        panels.add(createTestPanel());

        double waistCircumference = MeasurementUtils.computeFullWaistCircumference(panels);

        // Just inside the dead-zone
        double result4_99 = MeasurementUtils.computeFullCircumference(panels, 4.99);
        assertEquals(waistCircumference, result4_99, 0.001,
                "dyMm=4.99 should be within dead-zone");

        // Just outside the dead-zone
        double result5_01 = MeasurementUtils.computeFullCircumference(panels, 5.01);
        double intersection5_01 = 2.0 * MeasurementUtils.computeHalfCircumference(panels, 5.01);
        assertEquals(intersection5_01, result5_01, 0.001,
                "dyMm=5.01 should be outside dead-zone");
    }
}
