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
 * Tests for MeasurementUtils.
 */
public class MeasurementUtilsTest {

    @Test
    public void testComputePanelWaistY0_Median() {
        // Create a waist curve with Y values: 100, 200, 300, 400, 500
        List<Pt> points = Arrays.asList(
            new Pt(0, 100),
            new Pt(10, 200),
            new Pt(20, 300),
            new Pt(30, 400),
            new Pt(40, 500)
        );
        Curve2D waist = new Curve2D("test_waist", points);
        
        double result = MeasurementUtils.computePanelWaistY0(waist);
        assertEquals(300.0, result, 0.01, "Median of odd number of points should be middle value");
    }

    @Test
    public void testComputePanelWaistY0_MedianEven() {
        // Create a waist curve with Y values: 100, 200, 300, 400
        List<Pt> points = Arrays.asList(
            new Pt(0, 100),
            new Pt(10, 200),
            new Pt(20, 300),
            new Pt(30, 400)
        );
        Curve2D waist = new Curve2D("test_waist", points);
        
        double result = MeasurementUtils.computePanelWaistY0(waist);
        assertEquals(250.0, result, 0.01, "Median of even number of points should be average of middle two");
    }

    @Test
    public void testComputePanelWaistY0_Null() {
        double result = MeasurementUtils.computePanelWaistY0(null);
        assertEquals(0.0, result, 0.01, "Null curve should return 0");
    }

    // Commented out - computeCurveLength method not yet implemented
    // @Test
    // public void testComputeCurveLength_SimpleLine() {
    //     // Create a simple horizontal line: (0,0) -> (100,0)
    //     List<Pt> points = Arrays.asList(
    //         new Pt(0, 0),
    //         new Pt(100, 0)
    //     );
    //     Curve2D curve = new Curve2D("test_line", points);
    //     
    //     double result = MeasurementUtils.computeCurveLength(curve);
    //     assertEquals(100.0, result, 0.01, "Length of horizontal line should be 100");
    // }

    // @Test
    // public void testComputeCurveLength_Diagonal() {
    //     // Create a 3-4-5 right triangle: (0,0) -> (3,4)
    //     List<Pt> points = Arrays.asList(
    //         new Pt(0, 0),
    //         new Pt(3, 4)
    //     );
    //     Curve2D curve = new Curve2D("test_diagonal", points);
    //     
    //     double result = MeasurementUtils.computeCurveLength(curve);
    //     assertEquals(5.0, result, 0.01, "Length of 3-4-5 triangle hypotenuse should be 5");
    // }

    // @Test
    // public void testComputeCurveLength_MultiSegment() {
    //     // Create a polyline with multiple segments
    //     List<Pt> points = Arrays.asList(
    //         new Pt(0, 0),
    //         new Pt(10, 0),
    //         new Pt(10, 10),
    //         new Pt(20, 10)
    //     );
    //     Curve2D curve = new Curve2D("test_multi", points);
    //     
    //     double result = MeasurementUtils.computeCurveLength(curve);
    //     assertEquals(30.0, result, 0.01, "Total length should be 10+10+10=30");
    // }

    @Test
    public void testComputePanelWidthAtDy() {
        // Create a simple rectangular panel
        // Waist at y=100, left seam at x=0, right seam at x=50
        List<Pt> waistPoints = Arrays.asList(
            new Pt(0, 100),
            new Pt(50, 100)
        );
        Curve2D waist = new Curve2D("waist", waistPoints);
        
        // Left seam (vertical line at x=0, y from 0 to 200)
        List<Pt> leftPoints = Arrays.asList(
            new Pt(0, 0),
            new Pt(0, 200)
        );
        Curve2D leftSeam = new Curve2D("left", leftPoints);
        
        // Right seam (vertical line at x=50, y from 0 to 200)
        List<Pt> rightPoints = Arrays.asList(
            new Pt(50, 0),
            new Pt(50, 200)
        );
        Curve2D rightSeam = new Curve2D("right", rightPoints);
        
        PanelCurves panel = new PanelCurves(
            PanelId.A,
            null, // top
            null, // bottom
            waist,
            leftSeam, // seamToPrevUp
            null, // seamToPrevDown
            rightSeam, // seamToNextUp
            null  // seamToNextDown
        );
        
        // Test at waist level (dyMm = 0)
        double width = MeasurementUtils.computePanelWidthAtDy(panel, 0.0).orElse(0.0);
        assertEquals(50.0, width, 0.01, "Width at waist should be 50");
        
        // Test above waist (dyMm = 50, targetY = 50)
        width = MeasurementUtils.computePanelWidthAtDy(panel, 50.0).orElse(0.0);
        assertEquals(50.0, width, 0.01, "Width 50mm above waist should still be 50");
        
        // Test below waist (dyMm = -50, targetY = 150)
        width = MeasurementUtils.computePanelWidthAtDy(panel, -50.0).orElse(0.0);
        assertEquals(50.0, width, 0.01, "Width 50mm below waist should still be 50");
    }

    @Test
    public void testComputeFullCircumference() {
        // Create two simple panels, each 50mm wide
        List<Pt> waistPoints = Arrays.asList(
            new Pt(0, 100),
            new Pt(50, 100)
        );
        Curve2D waist = new Curve2D("waist", waistPoints);
        
        List<Pt> leftPoints = Arrays.asList(
            new Pt(0, 0),
            new Pt(0, 200)
        );
        Curve2D leftSeam = new Curve2D("left", leftPoints);
        
        List<Pt> rightPoints = Arrays.asList(
            new Pt(50, 0),
            new Pt(50, 200)
        );
        Curve2D rightSeam = new Curve2D("right", rightPoints);
        
        PanelCurves panel1 = new PanelCurves(
            PanelId.A, null, null, waist, leftSeam, null, rightSeam, null
        );
        PanelCurves panel2 = new PanelCurves(
            PanelId.B, null, null, waist, leftSeam, null, rightSeam, null
        );
        
        List<PanelCurves> panels = Arrays.asList(panel1, panel2);
        
        // Half circumference should be 50 + 50 = 100
        double halfCirc = MeasurementUtils.computeHalfCircumference(panels, 0.0);
        assertEquals(100.0, halfCirc, 0.01, "Half circumference should be 100");
        
        // Full circumference should be 2 * 100 = 200
        double fullCirc = MeasurementUtils.computeFullCircumference(panels, 0.0);
        assertEquals(200.0, fullCirc, 0.01, "Full circumference should be 200");
    }

    @Test
    public void testComputeCurveLengthPortion_Above() {
        // Create a vertical line from y=0 to y=200, split at y=100
        List<Pt> points = Arrays.asList(
            new Pt(0, 0),
            new Pt(0, 100),
            new Pt(0, 200)
        );
        Curve2D curve = new Curve2D("test_vertical", points);
        
        // Length above waist (y < 100) should be 100
        double lengthAbove = MeasurementUtils.computeCurveLengthPortion(curve, 100.0, true);
        assertEquals(100.0, lengthAbove, 0.01, "Length above waist should be 100");
        
        // Length below waist (y >= 100) should be 100
        double lengthBelow = MeasurementUtils.computeCurveLengthPortion(curve, 100.0, false);
        assertEquals(100.0, lengthBelow, 0.01, "Length below waist should be 100");
    }

    @Test
    public void testComputeCurveLengthPortion_CrossingSegment() {
        // Create a diagonal line that crosses waist at y=50
        // From (0, 0) to (100, 100)
        List<Pt> points = Arrays.asList(
            new Pt(0, 0),
            new Pt(100, 100)
        );
        Curve2D curve = new Curve2D("test_diagonal", points);
        
        double waistY = 50.0;
        
        // Total length is sqrt(100^2 + 100^2) = 141.42
        // Split at y=50 means split at x=50
        // Each half should be sqrt(50^2 + 50^2) = 70.71
        double lengthAbove = MeasurementUtils.computeCurveLengthPortion(curve, waistY, true);
        double lengthBelow = MeasurementUtils.computeCurveLengthPortion(curve, waistY, false);
        
        assertEquals(70.71, lengthAbove, 0.01, "Length above should be ~70.71");
        assertEquals(70.71, lengthBelow, 0.01, "Length below should be ~70.71");
        
        // Total should equal full curve length (both portions)
        double total = lengthAbove + lengthBelow;
        assertEquals(141.42, total, 0.01, "Sum of portions should equal full length");
    }

    @Test
    public void testComputePanelWidthAtDy_PositiveDy() {
        // Create a simple rectangular panel with waist at y=100
        List<Pt> waistPoints = Arrays.asList(
            new Pt(0, 100),
            new Pt(50, 100)
        );
        Curve2D waist = new Curve2D("waist", waistPoints);
        
        // Left seam (vertical line at x=0, y from 0 to 200)
        List<Pt> leftPoints = Arrays.asList(
            new Pt(0, 0),
            new Pt(0, 200)
        );
        Curve2D leftSeam = new Curve2D("left", leftPoints);
        
        // Right seam (vertical line at x=50, y from 0 to 200)
        List<Pt> rightPoints = Arrays.asList(
            new Pt(50, 0),
            new Pt(50, 200)
        );
        Curve2D rightSeam = new Curve2D("right", rightPoints);
        
        PanelCurves panel = new PanelCurves(
            PanelId.A, null, null, waist,
            leftSeam, null, rightSeam, null
        );
        
        // Test positive dy (upwards): dy=50 means y=100-50=50
        double width = MeasurementUtils.computePanelWidthAtDy(panel, 50.0).orElse(-1.0);
        assertEquals(50.0, width, 0.01, "Width 50mm above waist should be 50");
    }

    @Test
    public void testComputeValidDyRange() {
        // Create a panel with vertical seams from y=20 to y=180
        // Waist at y=100
        List<Pt> waistPoints = Arrays.asList(
            new Pt(0, 100),
            new Pt(50, 100)
        );
        Curve2D waist = new Curve2D("waist", waistPoints);
        
        List<Pt> leftPoints = Arrays.asList(
            new Pt(0, 20),
            new Pt(0, 180)
        );
        Curve2D leftSeam = new Curve2D("left", leftPoints);
        
        List<Pt> rightPoints = Arrays.asList(
            new Pt(50, 20),
            new Pt(50, 180)
        );
        Curve2D rightSeam = new Curve2D("right", rightPoints);
        
        PanelCurves panel = new PanelCurves(
            PanelId.A, null, null, waist,
            leftSeam, null, rightSeam, null
        );
        
        List<PanelCurves> panels = Arrays.asList(panel);
        
        MeasurementUtils.DyRange range = MeasurementUtils.computeValidDyRange(panels);
        
        // maxUp should be waistY - minY = 100 - 20 = 80
        assertEquals(80.0, range.maxUp, 0.01, "maxUp should be 80");
        
        // maxDown should be waistY - maxY = 100 - 180 = -80
        assertEquals(-80.0, range.maxDown, 0.01, "maxDown should be -80");
    }

    @Test
    public void testComputeValidDyRange_MultiplePanels() {
        // Panel 1: y range [20, 180], waist at 100
        List<Pt> waist1 = Arrays.asList(new Pt(0, 100), new Pt(50, 100));
        List<Pt> left1 = Arrays.asList(new Pt(0, 20), new Pt(0, 180));
        List<Pt> right1 = Arrays.asList(new Pt(50, 20), new Pt(50, 180));
        PanelCurves panel1 = new PanelCurves(
            PanelId.A, null, null, new Curve2D("w", waist1),
            new Curve2D("l", left1), null, new Curve2D("r", right1), null
        );
        
        // Panel 2: y range [40, 160], waist at 100
        List<Pt> waist2 = Arrays.asList(new Pt(50, 100), new Pt(100, 100));
        List<Pt> left2 = Arrays.asList(new Pt(50, 40), new Pt(50, 160));
        List<Pt> right2 = Arrays.asList(new Pt(100, 40), new Pt(100, 160));
        PanelCurves panel2 = new PanelCurves(
            PanelId.B, null, null, new Curve2D("w", waist2),
            new Curve2D("l", left2), null, new Curve2D("r", right2), null
        );
        
        List<PanelCurves> panels = Arrays.asList(panel1, panel2);
        
        MeasurementUtils.DyRange range = MeasurementUtils.computeValidDyRange(panels);
        
        // Intersection: min(80, 60) = 60 for maxUp
        assertEquals(60.0, range.maxUp, 0.01, "maxUp should be 60 (intersection)");
        
        // Intersection: max(-80, -60) = -60 for maxDown
        assertEquals(-60.0, range.maxDown, 0.01, "maxDown should be -60 (intersection)");
    }

    @Test
    public void testCanMeasureCircumferenceAtDy() {
        // Create a panel with valid range
        List<Pt> waistPoints = Arrays.asList(new Pt(0, 100), new Pt(50, 100));
        List<Pt> leftPoints = Arrays.asList(new Pt(0, 20), new Pt(0, 180));
        List<Pt> rightPoints = Arrays.asList(new Pt(50, 20), new Pt(50, 180));
        
        PanelCurves panel = new PanelCurves(
            PanelId.A, null, null, new Curve2D("w", waistPoints),
            new Curve2D("l", leftPoints), null, new Curve2D("r", rightPoints), null
        );
        
        List<PanelCurves> panels = Arrays.asList(panel);
        
        // Should be able to measure within range
        assertTrue(MeasurementUtils.canMeasureCircumferenceAtDy(panels, 0.0), 
                   "Should measure at waist");
        assertTrue(MeasurementUtils.canMeasureCircumferenceAtDy(panels, 50.0), 
                   "Should measure 50mm above waist");
        assertTrue(MeasurementUtils.canMeasureCircumferenceAtDy(panels, -50.0), 
                   "Should measure 50mm below waist");
        
        // Should not be able to measure outside range
        assertFalse(MeasurementUtils.canMeasureCircumferenceAtDy(panels, 100.0), 
                    "Should not measure 100mm above waist (out of range)");
        assertFalse(MeasurementUtils.canMeasureCircumferenceAtDy(panels, -100.0), 
                    "Should not measure 100mm below waist (out of range)");
    }

    // Commented out - computeSeamLengths method not yet implemented
    // @Test
    // public void testComputeSeamLengths() {
    //     // Create a panel with seams
    //     List<Pt> waistPoints = Arrays.asList(
    //         new Pt(0, 100),
    //         new Pt(50, 100)
    //     );
    //     Curve2D waist = new Curve2D("waist", waistPoints);
    //     
    //     // UP seam: vertical line from y=0 to y=200
    //     List<Pt> upPoints = Arrays.asList(
    //         new Pt(50, 0),
    //         new Pt(50, 200)
    //     );
    //     Curve2D upSeam = new Curve2D("up", upPoints);
    //     
    //     // DOWN seam: also vertical, slightly offset
    //     List<Pt> downPoints = Arrays.asList(
    //         new Pt(52, 0),
    //         new Pt(52, 200)
    //     );
    //     Curve2D downSeam = new Curve2D("down", downPoints);
    //     
    //     PanelCurves panel = new PanelCurves(
    //         PanelId.A, null, null, waist, null, null, upSeam, downSeam
    //     );
    //     
    //     MeasurementUtils.SeamLengths result = MeasurementUtils.computeSeamLengths(panel, null, "AB");
    //     
    //     assertEquals("AB", result.getSeamName());
    //     assertEquals(100.0, result.getUpAbove(), 0.01, "UP above should be 100");
    //     assertEquals(100.0, result.getUpBelow(), 0.01, "UP below should be 100");
    //     assertEquals(100.0, result.getDownAbove(), 0.01, "DOWN above should be 100");
    //     assertEquals(100.0, result.getDownBelow(), 0.01, "DOWN below should be 100");
    // }
}
