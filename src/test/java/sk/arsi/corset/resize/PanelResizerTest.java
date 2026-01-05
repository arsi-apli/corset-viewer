package sk.arsi.corset.resize;

import org.junit.jupiter.api.Test;
import sk.arsi.corset.model.Curve2D;
import sk.arsi.corset.model.PanelCurves;
import sk.arsi.corset.model.PanelId;
import sk.arsi.corset.model.Pt;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PanelResizerTest {
    
    @Test
    void testComputeSideShift() {
        // For 4 panels and 40mm circumference change
        // sideShift = 40 / (4 * 4) = 2.5mm
        assertEquals(2.5, PanelResizer.computeSideShift(40.0, 4), 0.001);
        
        // For 6 panels and 60mm circumference change
        // sideShift = 60 / (4 * 6) = 2.5mm
        assertEquals(2.5, PanelResizer.computeSideShift(60.0, 6), 0.001);
        
        // Negative (shrink)
        assertEquals(-2.5, PanelResizer.computeSideShift(-40.0, 4), 0.001);
        
        // Zero panels
        assertEquals(0.0, PanelResizer.computeSideShift(40.0, 0), 0.001);
    }
    
    @Test
    void testShiftPointsX() {
        List<Pt> points = Arrays.asList(
            new Pt(10.0, 20.0),
            new Pt(15.0, 25.0),
            new Pt(20.0, 30.0)
        );
        
        List<Pt> shifted = PanelResizer.shiftPointsX(points, 5.0);
        
        assertNotNull(shifted);
        assertEquals(3, shifted.size());
        assertEquals(15.0, shifted.get(0).getX(), 0.001);
        assertEquals(20.0, shifted.get(0).getY(), 0.001);
        assertEquals(20.0, shifted.get(1).getX(), 0.001);
        assertEquals(25.0, shifted.get(1).getY(), 0.001);
        assertEquals(25.0, shifted.get(2).getX(), 0.001);
        assertEquals(30.0, shifted.get(2).getY(), 0.001);
    }
    
    @Test
    void testShiftPointsXNegative() {
        List<Pt> points = Arrays.asList(
            new Pt(10.0, 20.0),
            new Pt(15.0, 25.0)
        );
        
        List<Pt> shifted = PanelResizer.shiftPointsX(points, -3.0);
        
        assertNotNull(shifted);
        assertEquals(2, shifted.size());
        assertEquals(7.0, shifted.get(0).getX(), 0.001);
        assertEquals(20.0, shifted.get(0).getY(), 0.001);
        assertEquals(12.0, shifted.get(1).getX(), 0.001);
        assertEquals(25.0, shifted.get(1).getY(), 0.001);
    }
    
    @Test
    void testResizeCurve() {
        List<Pt> points = Arrays.asList(
            new Pt(10.0, 20.0),
            new Pt(15.0, 25.0)
        );
        
        Curve2D curve = new Curve2D("TEST_CURVE", points);
        Curve2D resized = PanelResizer.resizeCurve(curve, 5.0);
        
        assertNotNull(resized);
        assertEquals("TEST_CURVE", resized.getId());
        assertEquals(2, resized.getPoints().size());
        assertEquals(15.0, resized.getPoints().get(0).getX(), 0.001);
        assertEquals(20.0, resized.getPoints().get(0).getY(), 0.001);
    }
    
    @Test
    void testResizeCurveZeroShift() {
        List<Pt> points = Arrays.asList(
            new Pt(10.0, 20.0),
            new Pt(15.0, 25.0)
        );
        
        Curve2D curve = new Curve2D("TEST_CURVE", points);
        Curve2D resized = PanelResizer.resizeCurve(curve, 0.0);
        
        assertNotNull(resized);
        // Should return original when shift is negligible
        assertEquals(curve, resized);
    }
    
    @Test
    void testIsLeftSide() {
        // Create a simple panel with waist centered at x=50
        List<Pt> waistPoints = Arrays.asList(
            new Pt(40.0, 100.0),
            new Pt(50.0, 100.0),
            new Pt(60.0, 100.0)
        );
        Curve2D waist = new Curve2D("TEST_WAIST", waistPoints);
        
        // Left seam at x=30
        List<Pt> leftPoints = Arrays.asList(
            new Pt(30.0, 80.0),
            new Pt(30.0, 100.0),
            new Pt(30.0, 120.0)
        );
        Curve2D leftSeam = new Curve2D("LEFT_SEAM", leftPoints);
        
        // Right seam at x=70
        List<Pt> rightPoints = Arrays.asList(
            new Pt(70.0, 80.0),
            new Pt(70.0, 100.0),
            new Pt(70.0, 120.0)
        );
        Curve2D rightSeam = new Curve2D("RIGHT_SEAM", rightPoints);
        
        List<Pt> topPoints = Arrays.asList(new Pt(50.0, 80.0), new Pt(50.0, 80.0));
        List<Pt> bottomPoints = Arrays.asList(new Pt(50.0, 120.0), new Pt(50.0, 120.0));
        
        PanelCurves panel = new PanelCurves(
            PanelId.A,
            new Curve2D("TOP", topPoints),
            new Curve2D("BOTTOM", bottomPoints),
            waist,
            leftSeam,
            leftSeam,
            rightSeam,
            rightSeam
        );
        
        assertTrue(PanelResizer.isLeftSide(leftSeam, panel));
        assertFalse(PanelResizer.isLeftSide(rightSeam, panel));
    }
    
    @Test
    void testResizePanel() {
        // Create a simple panel
        List<Pt> waistPoints = Arrays.asList(new Pt(50.0, 100.0), new Pt(60.0, 100.0));
        List<Pt> topPoints = Arrays.asList(new Pt(50.0, 80.0), new Pt(60.0, 80.0));
        List<Pt> bottomPoints = Arrays.asList(new Pt(50.0, 120.0), new Pt(60.0, 120.0));
        List<Pt> leftPoints = Arrays.asList(new Pt(50.0, 80.0), new Pt(50.0, 100.0));
        List<Pt> rightPoints = Arrays.asList(new Pt(60.0, 80.0), new Pt(60.0, 100.0));
        
        PanelCurves panel = new PanelCurves(
            PanelId.A,
            new Curve2D("A_TOP", topPoints),
            new Curve2D("A_BOTTOM", bottomPoints),
            new Curve2D("A_WAIST", waistPoints),
            new Curve2D("AA_UP", leftPoints),
            new Curve2D("AA_DOWN", leftPoints),
            new Curve2D("AB_UP", rightPoints),
            new Curve2D("AB_DOWN", rightPoints)
        );
        
        // Resize with GLOBAL mode, 40mm change, 4 panels
        // sideShift = 40 / 16 = 2.5mm
        // leftShift = -2.5, rightShift = 2.5
        ResizedPanel resized = PanelResizer.resizePanel(panel, ResizeMode.GLOBAL, 40.0, 4);
        
        assertNotNull(resized);
        assertEquals(ResizeMode.GLOBAL, resized.getMode());
        assertNotNull(resized.getTop());
        assertNotNull(resized.getBottom());
        assertNotNull(resized.getWaist());
    }
    
    @Test
    void testResizePanelEdgeCurve_BothEndsMove() {
        // Create a curve spanning from x=10 to x=90
        List<Pt> points = Arrays.asList(
            new Pt(10.0, 50.0),  // leftmost
            new Pt(30.0, 45.0),
            new Pt(50.0, 40.0),  // center
            new Pt(70.0, 45.0),
            new Pt(90.0, 50.0)   // rightmost
        );
        
        Curve2D curve = new Curve2D("TEST_EDGE", points);
        
        // leftShift = -5.0, rightShift = 5.0 (simulating grow operation)
        Curve2D resized = PanelResizer.resizePanelEdgeCurve(curve, -5.0, 5.0);
        
        assertNotNull(resized);
        assertEquals(5, resized.getPoints().size());
        
        // Leftmost point should shift by leftShift (-5.0)
        assertEquals(10.0 - 5.0, resized.getPoints().get(0).getX(), 0.001);
        assertEquals(50.0, resized.getPoints().get(0).getY(), 0.001);
        
        // Rightmost point should shift by rightShift (+5.0)
        assertEquals(90.0 + 5.0, resized.getPoints().get(4).getX(), 0.001);
        assertEquals(50.0, resized.getPoints().get(4).getY(), 0.001);
        
        // Center point should have interpolated shift (0.0 for this symmetric case)
        // t = (50 - 10) / (90 - 10) = 40/80 = 0.5
        // shift = -5.0 + 0.5 * (5.0 - (-5.0)) = -5.0 + 0.5 * 10.0 = 0.0
        assertEquals(50.0, resized.getPoints().get(2).getX(), 0.001);
        assertEquals(40.0, resized.getPoints().get(2).getY(), 0.001);
    }
    
    @Test
    void testResizePanelEdgeCurve_InterpolationWorks() {
        // Create a curve with known X positions
        List<Pt> points = Arrays.asList(
            new Pt(0.0, 10.0),   // t=0.0
            new Pt(25.0, 20.0),  // t=0.25
            new Pt(50.0, 30.0),  // t=0.5
            new Pt(75.0, 40.0),  // t=0.75
            new Pt(100.0, 50.0)  // t=1.0
        );
        
        Curve2D curve = new Curve2D("TEST_INTERP", points);
        
        // leftShift = -10.0, rightShift = 10.0
        Curve2D resized = PanelResizer.resizePanelEdgeCurve(curve, -10.0, 10.0);
        
        assertNotNull(resized);
        
        // t=0.0: shift = -10.0
        assertEquals(0.0 - 10.0, resized.getPoints().get(0).getX(), 0.001);
        
        // t=0.25: shift = -10.0 + 0.25 * 20.0 = -10.0 + 5.0 = -5.0
        assertEquals(25.0 - 5.0, resized.getPoints().get(1).getX(), 0.001);
        
        // t=0.5: shift = -10.0 + 0.5 * 20.0 = 0.0
        assertEquals(50.0, resized.getPoints().get(2).getX(), 0.001);
        
        // t=0.75: shift = -10.0 + 0.75 * 20.0 = -10.0 + 15.0 = 5.0
        assertEquals(75.0 + 5.0, resized.getPoints().get(3).getX(), 0.001);
        
        // t=1.0: shift = 10.0
        assertEquals(100.0 + 10.0, resized.getPoints().get(4).getX(), 0.001);
        
        // Y coordinates should remain unchanged
        for (int i = 0; i < points.size(); i++) {
            assertEquals(points.get(i).getY(), resized.getPoints().get(i).getY(), 0.001);
        }
    }
    
    @Test
    void testResizeSeamCurve_UniformShift() {
        // Create a seam curve (all points should shift uniformly)
        List<Pt> points = Arrays.asList(
            new Pt(10.0, 20.0),
            new Pt(10.5, 30.0),
            new Pt(11.0, 40.0)
        );
        
        Curve2D seam = new Curve2D("TEST_SEAM", points);
        
        // Shift by 3.0
        Curve2D resized = PanelResizer.resizeSeamCurve(seam, 3.0);
        
        assertNotNull(resized);
        assertEquals(3, resized.getPoints().size());
        
        // All points should shift by exactly 3.0
        assertEquals(13.0, resized.getPoints().get(0).getX(), 0.001);
        assertEquals(20.0, resized.getPoints().get(0).getY(), 0.001);
        
        assertEquals(13.5, resized.getPoints().get(1).getX(), 0.001);
        assertEquals(30.0, resized.getPoints().get(1).getY(), 0.001);
        
        assertEquals(14.0, resized.getPoints().get(2).getX(), 0.001);
        assertEquals(40.0, resized.getPoints().get(2).getY(), 0.001);
    }
    
    @Test
    void testGlobalMode_WaistMovesAtBothEnds() {
        // Create a panel where waist spans from x=10 to x=90
        List<Pt> topPoints = Arrays.asList(new Pt(10.0, 0.0), new Pt(90.0, 0.0));
        List<Pt> waistPoints = Arrays.asList(new Pt(10.0, 50.0), new Pt(50.0, 50.0), new Pt(90.0, 50.0));
        List<Pt> bottomPoints = Arrays.asList(new Pt(10.0, 100.0), new Pt(90.0, 100.0));
        List<Pt> leftSeamPoints = Arrays.asList(new Pt(10.0, 0.0), new Pt(10.0, 100.0));
        List<Pt> rightSeamPoints = Arrays.asList(new Pt(90.0, 0.0), new Pt(90.0, 100.0));
        
        PanelCurves panel = new PanelCurves(
            PanelId.A,
            new Curve2D("A_TOP", topPoints),
            new Curve2D("A_BOTTOM", bottomPoints),
            new Curve2D("A_WAIST", waistPoints),
            new Curve2D("AA_UP", leftSeamPoints),
            new Curve2D("AA_DOWN", leftSeamPoints),
            new Curve2D("AB_UP", rightSeamPoints),
            new Curve2D("AB_DOWN", rightSeamPoints)
        );
        
        // Resize with GLOBAL mode, 40mm change, 4 panels
        // sideShift = 40 / 16 = 2.5mm
        // leftShift = -2.5, rightShift = 2.5
        ResizedPanel resized = PanelResizer.resizePanel(panel, ResizeMode.GLOBAL, 40.0, 4);
        
        // Check waist: leftmost should shift left, rightmost should shift right
        Curve2D resizedWaist = resized.getWaist();
        assertNotNull(resizedWaist);
        assertEquals(3, resizedWaist.getPoints().size());
        
        // Left endpoint: x=10, should shift by -2.5
        assertEquals(10.0 - 2.5, resizedWaist.getPoints().get(0).getX(), 0.001);
        
        // Right endpoint: x=90, should shift by +2.5
        assertEquals(90.0 + 2.5, resizedWaist.getPoints().get(2).getX(), 0.001);
        
        // Center point: x=50, t=0.5, should have zero shift
        assertEquals(50.0, resizedWaist.getPoints().get(1).getX(), 0.001);
    }
    
    @Test
    void testTopMode_WaistUnchanged() {
        // Create a panel
        List<Pt> topPoints = Arrays.asList(new Pt(10.0, 0.0), new Pt(50.0, 0.0), new Pt(90.0, 0.0));
        List<Pt> waistPoints = Arrays.asList(new Pt(10.0, 50.0), new Pt(50.0, 50.0), new Pt(90.0, 50.0));
        List<Pt> bottomPoints = Arrays.asList(new Pt(10.0, 100.0), new Pt(50.0, 100.0), new Pt(90.0, 100.0));
        List<Pt> leftSeamPoints = Arrays.asList(new Pt(10.0, 0.0), new Pt(10.0, 100.0));
        List<Pt> rightSeamPoints = Arrays.asList(new Pt(90.0, 0.0), new Pt(90.0, 100.0));
        
        PanelCurves panel = new PanelCurves(
            PanelId.A,
            new Curve2D("A_TOP", topPoints),
            new Curve2D("A_BOTTOM", bottomPoints),
            new Curve2D("A_WAIST", waistPoints),
            new Curve2D("AA_UP", leftSeamPoints),
            new Curve2D("AA_DOWN", leftSeamPoints),
            new Curve2D("AB_UP", rightSeamPoints),
            new Curve2D("AB_DOWN", rightSeamPoints)
        );
        
        // Resize with TOP mode
        ResizedPanel resized = PanelResizer.resizePanel(panel, ResizeMode.TOP, 40.0, 4);
        
        // Waist should be unchanged (same reference)
        assertSame(panel.getWaist(), resized.getWaist());
        
        // Bottom should be unchanged (same reference)
        assertSame(panel.getBottom(), resized.getBottom());
        
        // Top should be resized (different reference)
        assertNotSame(panel.getTop(), resized.getTop());
        
        // Verify waist points are exactly the same
        Curve2D resizedWaist = resized.getWaist();
        for (int i = 0; i < waistPoints.size(); i++) {
            assertEquals(waistPoints.get(i).getX(), resizedWaist.getPoints().get(i).getX(), 0.001);
            assertEquals(waistPoints.get(i).getY(), resizedWaist.getPoints().get(i).getY(), 0.001);
        }
    }
    
    @Test
    void testBottomMode_WaistUnchanged() {
        // Create a panel
        List<Pt> topPoints = Arrays.asList(new Pt(10.0, 0.0), new Pt(50.0, 0.0), new Pt(90.0, 0.0));
        List<Pt> waistPoints = Arrays.asList(new Pt(10.0, 50.0), new Pt(50.0, 50.0), new Pt(90.0, 50.0));
        List<Pt> bottomPoints = Arrays.asList(new Pt(10.0, 100.0), new Pt(50.0, 100.0), new Pt(90.0, 100.0));
        List<Pt> leftSeamPoints = Arrays.asList(new Pt(10.0, 0.0), new Pt(10.0, 100.0));
        List<Pt> rightSeamPoints = Arrays.asList(new Pt(90.0, 0.0), new Pt(90.0, 100.0));
        
        PanelCurves panel = new PanelCurves(
            PanelId.A,
            new Curve2D("A_TOP", topPoints),
            new Curve2D("A_BOTTOM", bottomPoints),
            new Curve2D("A_WAIST", waistPoints),
            new Curve2D("AA_UP", leftSeamPoints),
            new Curve2D("AA_DOWN", leftSeamPoints),
            new Curve2D("AB_UP", rightSeamPoints),
            new Curve2D("AB_DOWN", rightSeamPoints)
        );
        
        // Resize with BOTTOM mode
        ResizedPanel resized = PanelResizer.resizePanel(panel, ResizeMode.BOTTOM, 40.0, 4);
        
        // Waist should be unchanged (same reference)
        assertSame(panel.getWaist(), resized.getWaist());
        
        // Top should be unchanged (same reference)
        assertSame(panel.getTop(), resized.getTop());
        
        // Bottom should be resized (different reference)
        assertNotSame(panel.getBottom(), resized.getBottom());
        
        // Verify waist points are exactly the same
        Curve2D resizedWaist = resized.getWaist();
        for (int i = 0; i < waistPoints.size(); i++) {
            assertEquals(waistPoints.get(i).getX(), resizedWaist.getPoints().get(i).getX(), 0.001);
            assertEquals(waistPoints.get(i).getY(), resizedWaist.getPoints().get(i).getY(), 0.001);
        }
    }
    
    @Test
    void testTopMode_BottomAndDownSeamsUnchanged() {
        // Create a panel
        List<Pt> topPoints = Arrays.asList(new Pt(10.0, 0.0), new Pt(90.0, 0.0));
        List<Pt> waistPoints = Arrays.asList(new Pt(10.0, 50.0), new Pt(90.0, 50.0));
        List<Pt> bottomPoints = Arrays.asList(new Pt(10.0, 100.0), new Pt(90.0, 100.0));
        List<Pt> leftSeamUpPoints = Arrays.asList(new Pt(10.0, 0.0), new Pt(10.0, 50.0));
        List<Pt> leftSeamDownPoints = Arrays.asList(new Pt(10.0, 50.0), new Pt(10.0, 100.0));
        List<Pt> rightSeamUpPoints = Arrays.asList(new Pt(90.0, 0.0), new Pt(90.0, 50.0));
        List<Pt> rightSeamDownPoints = Arrays.asList(new Pt(90.0, 50.0), new Pt(90.0, 100.0));
        
        PanelCurves panel = new PanelCurves(
            PanelId.A,
            new Curve2D("A_TOP", topPoints),
            new Curve2D("A_BOTTOM", bottomPoints),
            new Curve2D("A_WAIST", waistPoints),
            new Curve2D("AA_UP", leftSeamUpPoints),
            new Curve2D("AA_DOWN", leftSeamDownPoints),
            new Curve2D("AB_UP", rightSeamUpPoints),
            new Curve2D("AB_DOWN", rightSeamDownPoints)
        );
        
        // Resize with TOP mode
        ResizedPanel resized = PanelResizer.resizePanel(panel, ResizeMode.TOP, 40.0, 4);
        
        // DOWN seams should be unchanged
        assertSame(panel.getSeamToPrevDown(), resized.getSeamToPrevDown());
        assertSame(panel.getSeamToNextDown(), resized.getSeamToNextDown());
        
        // UP seams should be resized
        assertNotSame(panel.getSeamToPrevUp(), resized.getSeamToPrevUp());
        assertNotSame(panel.getSeamToNextUp(), resized.getSeamToNextUp());
    }
    
    @Test
    void testBottomMode_TopAndUpSeamsUnchanged() {
        // Create a panel
        List<Pt> topPoints = Arrays.asList(new Pt(10.0, 0.0), new Pt(90.0, 0.0));
        List<Pt> waistPoints = Arrays.asList(new Pt(10.0, 50.0), new Pt(90.0, 50.0));
        List<Pt> bottomPoints = Arrays.asList(new Pt(10.0, 100.0), new Pt(90.0, 100.0));
        List<Pt> leftSeamUpPoints = Arrays.asList(new Pt(10.0, 0.0), new Pt(10.0, 50.0));
        List<Pt> leftSeamDownPoints = Arrays.asList(new Pt(10.0, 50.0), new Pt(10.0, 100.0));
        List<Pt> rightSeamUpPoints = Arrays.asList(new Pt(90.0, 0.0), new Pt(90.0, 50.0));
        List<Pt> rightSeamDownPoints = Arrays.asList(new Pt(90.0, 50.0), new Pt(90.0, 100.0));
        
        PanelCurves panel = new PanelCurves(
            PanelId.A,
            new Curve2D("A_TOP", topPoints),
            new Curve2D("A_BOTTOM", bottomPoints),
            new Curve2D("A_WAIST", waistPoints),
            new Curve2D("AA_UP", leftSeamUpPoints),
            new Curve2D("AA_DOWN", leftSeamDownPoints),
            new Curve2D("AB_UP", rightSeamUpPoints),
            new Curve2D("AB_DOWN", rightSeamDownPoints)
        );
        
        // Resize with BOTTOM mode
        ResizedPanel resized = PanelResizer.resizePanel(panel, ResizeMode.BOTTOM, 40.0, 4);
        
        // UP seams should be unchanged
        assertSame(panel.getSeamToPrevUp(), resized.getSeamToPrevUp());
        assertSame(panel.getSeamToNextUp(), resized.getSeamToNextUp());
        
        // DOWN seams should be resized
        assertNotSame(panel.getSeamToPrevDown(), resized.getSeamToPrevDown());
        assertNotSame(panel.getSeamToNextDown(), resized.getSeamToNextDown());
    }
}
