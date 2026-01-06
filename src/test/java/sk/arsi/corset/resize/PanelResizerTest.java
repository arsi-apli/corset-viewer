package sk.arsi.corset.resize;

import org.junit.jupiter.api.Test;
import sk.arsi.corset.model.Curve2D;
import sk.arsi.corset.model.Pt;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PanelResizer.
 */
class PanelResizerTest {

    @Test
    void testCalculateSideShift() {
        // For 6 panels and deltaMm=24mm:
        // sideShift = 24 / (4 * 6) = 24 / 24 = 1.0mm
        double sideShift = PanelResizer.calculateSideShift(24.0, 6);
        assertEquals(1.0, sideShift, 0.001);

        // For 5 panels and deltaMm=40mm:
        // sideShift = 40 / (4 * 5) = 40 / 20 = 2.0mm
        sideShift = PanelResizer.calculateSideShift(40.0, 5);
        assertEquals(2.0, sideShift, 0.001);

        // Negative delta (shrink)
        sideShift = PanelResizer.calculateSideShift(-30.0, 6);
        assertEquals(-1.25, sideShift, 0.001);

        // Zero panels
        sideShift = PanelResizer.calculateSideShift(24.0, 0);
        assertEquals(0.0, sideShift, 0.001);
    }

    @Test
    void testResizeEdgeCurve_endpointsMoveInOppositeDirections() {
        // Create a simple horizontal edge curve from x=0 to x=100
        List<Pt> points = Arrays.asList(
            new Pt(0.0, 50.0),
            new Pt(50.0, 50.0),
            new Pt(100.0, 50.0)
        );
        Curve2D curve = new Curve2D("test-edge", points);

        double sideShift = 10.0;
        Curve2D resized = PanelResizer.resizeEdgeCurve(curve, sideShift);

        assertNotNull(resized);
        List<Pt> resizedPoints = resized.getPoints();
        assertEquals(3, resizedPoints.size());

        // Leftmost point (x=0) should shift by -sideShift
        Pt left = resizedPoints.get(0);
        assertEquals(-10.0, left.getX(), 0.001, "Leftmost point should shift left");
        assertEquals(50.0, left.getY(), 0.001, "Y should remain unchanged");

        // Rightmost point (x=100) should shift by +sideShift
        Pt right = resizedPoints.get(2);
        assertEquals(110.0, right.getX(), 0.001, "Rightmost point should shift right");
        assertEquals(50.0, right.getY(), 0.001, "Y should remain unchanged");

        // Middle point (x=50) should not shift (t=0.5, dx=0)
        Pt middle = resizedPoints.get(1);
        assertEquals(50.0, middle.getX(), 0.001, "Middle point should not shift");
        assertEquals(50.0, middle.getY(), 0.001, "Y should remain unchanged");
    }

    @Test
    void testResizeEdgeCurve_yValuesUnchanged() {
        // Create a curve with varying Y values
        List<Pt> points = Arrays.asList(
            new Pt(0.0, 10.0),
            new Pt(25.0, 20.0),
            new Pt(50.0, 30.0),
            new Pt(75.0, 25.0),
            new Pt(100.0, 15.0)
        );
        Curve2D curve = new Curve2D("test-varying-y", points);

        double sideShift = 5.0;
        Curve2D resized = PanelResizer.resizeEdgeCurve(curve, sideShift);

        assertNotNull(resized);
        List<Pt> resizedPoints = resized.getPoints();
        
        // Check that all Y values remain unchanged
        for (int i = 0; i < points.size(); i++) {
            assertEquals(points.get(i).getY(), resizedPoints.get(i).getY(), 0.001,
                "Y value should remain unchanged at index " + i);
        }
    }

    @Test
    void testResizeEdgeCurve_interpolation() {
        // Create a curve with 5 evenly spaced points from x=0 to x=100
        List<Pt> points = Arrays.asList(
            new Pt(0.0, 50.0),    // t=0.0
            new Pt(25.0, 50.0),   // t=0.25
            new Pt(50.0, 50.0),   // t=0.5
            new Pt(75.0, 50.0),   // t=0.75
            new Pt(100.0, 50.0)   // t=1.0
        );
        Curve2D curve = new Curve2D("test-interpolation", points);

        double sideShift = 10.0;
        Curve2D resized = PanelResizer.resizeEdgeCurve(curve, sideShift);

        List<Pt> resizedPoints = resized.getPoints();

        // t=0.0: dx = lerp(-10, +10, 0.0) = -10
        assertEquals(-10.0, resizedPoints.get(0).getX(), 0.001);

        // t=0.25: dx = lerp(-10, +10, 0.25) = -10 + 0.25*20 = -5
        assertEquals(20.0, resizedPoints.get(1).getX(), 0.001);

        // t=0.5: dx = lerp(-10, +10, 0.5) = 0
        assertEquals(50.0, resizedPoints.get(2).getX(), 0.001);

        // t=0.75: dx = lerp(-10, +10, 0.75) = -10 + 0.75*20 = 5
        assertEquals(80.0, resizedPoints.get(3).getX(), 0.001);

        // t=1.0: dx = lerp(-10, +10, 1.0) = +10
        assertEquals(110.0, resizedPoints.get(4).getX(), 0.001);
    }

    @Test
    void testResizeSeamCurve_uniformShift() {
        // Create a vertical seam curve
        List<Pt> points = Arrays.asList(
            new Pt(10.0, 0.0),
            new Pt(10.0, 50.0),
            new Pt(10.0, 100.0)
        );
        Curve2D curve = new Curve2D("test-seam", points);

        double shift = 5.0;
        Curve2D resized = PanelResizer.resizeSeamCurve(curve, shift);

        assertNotNull(resized);
        List<Pt> resizedPoints = resized.getPoints();
        assertEquals(3, resizedPoints.size());

        // All points should shift by the same amount
        for (int i = 0; i < points.size(); i++) {
            Pt original = points.get(i);
            Pt shifted = resizedPoints.get(i);
            
            assertEquals(original.getX() + shift, shifted.getX(), 0.001,
                "X should shift uniformly at index " + i);
            assertEquals(original.getY(), shifted.getY(), 0.001,
                "Y should remain unchanged at index " + i);
        }
    }

    @Test
    void testResizeSeamCurve_negativeShift() {
        // Test with negative shift (shift left)
        List<Pt> points = Arrays.asList(
            new Pt(20.0, 10.0),
            new Pt(20.0, 30.0)
        );
        Curve2D curve = new Curve2D("test-seam-negative", points);

        double shift = -7.5;
        Curve2D resized = PanelResizer.resizeSeamCurve(curve, shift);

        List<Pt> resizedPoints = resized.getPoints();
        
        assertEquals(12.5, resizedPoints.get(0).getX(), 0.001);
        assertEquals(10.0, resizedPoints.get(0).getY(), 0.001);
        assertEquals(12.5, resizedPoints.get(1).getX(), 0.001);
        assertEquals(30.0, resizedPoints.get(1).getY(), 0.001);
    }

    @Test
    void testResizeEdgeCurve_nullCurve() {
        Curve2D resized = PanelResizer.resizeEdgeCurve(null, 10.0);
        assertNull(resized);
    }

    @Test
    void testResizeSeamCurve_nullCurve() {
        Curve2D resized = PanelResizer.resizeSeamCurve(null, 10.0);
        assertNull(resized);
    }
}
