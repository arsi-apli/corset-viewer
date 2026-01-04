package sk.arsi.corset.export;

import org.junit.jupiter.api.Test;
import sk.arsi.corset.model.Curve2D;
import sk.arsi.corset.model.Pt;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for GeometryUtils.
 */
public class GeometryUtilsTest {

    @Test
    public void testGenerateNotchPositions_Count3() {
        List<Double> positions = GeometryUtils.generateNotchPositions(3);
        assertEquals(3, positions.size());
        assertEquals(0.25, positions.get(0), 0.001);
        assertEquals(0.50, positions.get(1), 0.001);
        assertEquals(0.75, positions.get(2), 0.001);
    }

    @Test
    public void testGenerateNotchPositions_Count1() {
        List<Double> positions = GeometryUtils.generateNotchPositions(1);
        assertEquals(1, positions.size());
        assertEquals(0.5, positions.get(0), 0.001);
    }

    @Test
    public void testGenerateNotchPositions_Count5() {
        List<Double> positions = GeometryUtils.generateNotchPositions(5);
        assertEquals(5, positions.size());
        // For count=5: i/(5+1) = i/6
        assertEquals(1.0/6, positions.get(0), 0.001);
        assertEquals(2.0/6, positions.get(1), 0.001);
        assertEquals(3.0/6, positions.get(2), 0.001);
        assertEquals(4.0/6, positions.get(3), 0.001);
        assertEquals(5.0/6, positions.get(4), 0.001);
    }

    @Test
    public void testGenerateNotchPositions_Count0() {
        List<Double> positions = GeometryUtils.generateNotchPositions(0);
        assertEquals(0, positions.size());
    }

    @Test
    public void testComputeArcLength_SimpleLine() {
        List<Pt> points = Arrays.asList(
            new Pt(0, 0),
            new Pt(100, 0)
        );
        double length = GeometryUtils.computeArcLength(points);
        assertEquals(100.0, length, 0.001);
    }

    @Test
    public void testComputeArcLength_MultiSegment() {
        List<Pt> points = Arrays.asList(
            new Pt(0, 0),
            new Pt(10, 0),
            new Pt(10, 10)
        );
        double length = GeometryUtils.computeArcLength(points);
        assertEquals(20.0, length, 0.001);
    }

    @Test
    public void testPointAtArcLength_HalfwayOnLine() {
        List<Pt> points = Arrays.asList(
            new Pt(0, 0),
            new Pt(100, 0)
        );
        Pt point = GeometryUtils.pointAtArcLength(points, 0.5);
        assertNotNull(point);
        assertEquals(50.0, point.getX(), 0.001);
        assertEquals(0.0, point.getY(), 0.001);
    }

    @Test
    public void testPointAtArcLength_QuarterOnLine() {
        List<Pt> points = Arrays.asList(
            new Pt(0, 0),
            new Pt(100, 0)
        );
        Pt point = GeometryUtils.pointAtArcLength(points, 0.25);
        assertNotNull(point);
        assertEquals(25.0, point.getX(), 0.001);
        assertEquals(0.0, point.getY(), 0.001);
    }

    @Test
    public void testPointAtArcLength_TwoSegments() {
        List<Pt> points = Arrays.asList(
            new Pt(0, 0),
            new Pt(10, 0),
            new Pt(10, 10)
        );
        // Total length = 20, 50% = 10 (end of first segment)
        Pt point = GeometryUtils.pointAtArcLength(points, 0.5);
        assertNotNull(point);
        assertEquals(10.0, point.getX(), 0.001);
        assertEquals(0.0, point.getY(), 0.001);
    }

    @Test
    public void testTangentAtArcLength_HorizontalLine() {
        List<Pt> points = Arrays.asList(
            new Pt(0, 0),
            new Pt(100, 0)
        );
        Pt tangent = GeometryUtils.tangentAtArcLength(points, 0.5);
        assertNotNull(tangent);
        assertEquals(1.0, tangent.getX(), 0.001);
        assertEquals(0.0, tangent.getY(), 0.001);
    }

    @Test
    public void testTangentAtArcLength_VerticalLine() {
        List<Pt> points = Arrays.asList(
            new Pt(0, 0),
            new Pt(0, 100)
        );
        Pt tangent = GeometryUtils.tangentAtArcLength(points, 0.5);
        assertNotNull(tangent);
        assertEquals(0.0, tangent.getX(), 0.001);
        assertEquals(1.0, tangent.getY(), 0.001);
    }

    @Test
    public void testTangentAtArcLength_DiagonalLine() {
        List<Pt> points = Arrays.asList(
            new Pt(0, 0),
            new Pt(3, 4)
        );
        Pt tangent = GeometryUtils.tangentAtArcLength(points, 0.5);
        assertNotNull(tangent);
        // Normalized (3, 4) = (0.6, 0.8)
        assertEquals(0.6, tangent.getX(), 0.001);
        assertEquals(0.8, tangent.getY(), 0.001);
    }

    @Test
    public void testComputePanelInterior_SimpleCurve() {
        List<Pt> waistPoints = Arrays.asList(
            new Pt(0, 100),
            new Pt(100, 100)
        );
        Curve2D waist = new Curve2D("waist", waistPoints);
        Pt interior = GeometryUtils.computePanelInterior(waist);
        assertNotNull(interior);
        assertEquals(50.0, interior.getX(), 0.001);
        assertEquals(100.0, interior.getY(), 0.001);
    }

    @Test
    public void testComputeInwardNormal_SimpleCase() {
        // Point at (50, 0), horizontal tangent (1, 0)
        // Interior at (50, 50) - above the point
        Pt point = new Pt(50, 0);
        Pt tangent = new Pt(1, 0);
        Pt interior = new Pt(50, 50);
        
        Pt inwardNormal = GeometryUtils.computeInwardNormal(point, tangent, interior);
        assertNotNull(inwardNormal);
        
        // For horizontal tangent (1, 0):
        // normal1 = (-0, 1) = (0, 1) - points up
        // normal2 = (0, -1) - points down
        // Interior is above (at y=50), so inward should be (0, 1)
        assertEquals(0.0, inwardNormal.getX(), 0.001);
        assertEquals(1.0, inwardNormal.getY(), 0.001);
    }

    @Test
    public void testComputeInwardNormal_VerticalSeam() {
        // Point at (0, 50), vertical tangent pointing up (0, 1)
        // Interior at (50, 50) - to the right
        Pt point = new Pt(0, 50);
        Pt tangent = new Pt(0, 1);
        Pt interior = new Pt(50, 50);
        
        Pt inwardNormal = GeometryUtils.computeInwardNormal(point, tangent, interior);
        assertNotNull(inwardNormal);
        
        // For vertical tangent pointing up (0, 1):
        // normal1 = (-1, 0) - points left
        // normal2 = (1, 0) - points right
        // Interior is to the right, so inward should be (1, 0)
        assertEquals(1.0, inwardNormal.getX(), 0.001);
        assertEquals(0.0, inwardNormal.getY(), 0.001);
    }

    @Test
    public void testCombineCurves_BothCurves() {
        List<Pt> upPoints = Arrays.asList(
            new Pt(0, 50),
            new Pt(0, 0)
        );
        Curve2D upCurve = new Curve2D("up", upPoints);
        
        List<Pt> downPoints = Arrays.asList(
            new Pt(0, 50),
            new Pt(0, 100)
        );
        Curve2D downCurve = new Curve2D("down", downPoints);
        
        List<Pt> combined = GeometryUtils.combineCurves(upCurve, downCurve);
        
        // Should go: top of UP (0,0), waist (0,50), bottom of DOWN (0,100)
        // UP reversed: (0,0), (0,50)
        // DOWN from index 1: (0,100)
        assertEquals(3, combined.size());
        assertEquals(0.0, combined.get(0).getX(), 0.001);
        assertEquals(0.0, combined.get(0).getY(), 0.001);
        assertEquals(0.0, combined.get(1).getX(), 0.001);
        assertEquals(50.0, combined.get(1).getY(), 0.001);
        assertEquals(0.0, combined.get(2).getX(), 0.001);
        assertEquals(100.0, combined.get(2).getY(), 0.001);
    }

    @Test
    public void testCombineCurves_OnlyUpCurve() {
        List<Pt> upPoints = Arrays.asList(
            new Pt(0, 50),
            new Pt(0, 0)
        );
        Curve2D upCurve = new Curve2D("up", upPoints);
        
        List<Pt> combined = GeometryUtils.combineCurves(upCurve, null);
        
        // Should be UP reversed: (0,0), (0,50)
        assertEquals(2, combined.size());
        assertEquals(0.0, combined.get(0).getX(), 0.001);
        assertEquals(0.0, combined.get(0).getY(), 0.001);
        assertEquals(0.0, combined.get(1).getX(), 0.001);
        assertEquals(50.0, combined.get(1).getY(), 0.001);
    }

    @Test
    public void testCombineCurves_OnlyDownCurve() {
        List<Pt> downPoints = Arrays.asList(
            new Pt(0, 50),
            new Pt(0, 100)
        );
        Curve2D downCurve = new Curve2D("down", downPoints);
        
        List<Pt> combined = GeometryUtils.combineCurves(null, downCurve);
        
        // Should be DOWN as-is: (0,50), (0,100)
        assertEquals(2, combined.size());
        assertEquals(0.0, combined.get(0).getX(), 0.001);
        assertEquals(50.0, combined.get(0).getY(), 0.001);
        assertEquals(0.0, combined.get(1).getX(), 0.001);
        assertEquals(100.0, combined.get(1).getY(), 0.001);
    }
}
