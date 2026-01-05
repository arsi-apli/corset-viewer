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
}
