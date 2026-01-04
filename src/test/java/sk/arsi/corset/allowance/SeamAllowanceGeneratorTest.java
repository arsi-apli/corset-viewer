package sk.arsi.corset.allowance;

import org.junit.jupiter.api.Test;
import sk.arsi.corset.model.Curve2D;
import sk.arsi.corset.model.Pt;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SeamAllowanceGenerator.
 */
public class SeamAllowanceGeneratorTest {

    private final SeamAllowanceGenerator generator = new SeamAllowanceGenerator();

    @Test
    public void testSimpleVerticalLineOffsetLeft() {
        // Simple vertical line from (0, 0) to (0, 100)
        List<Pt> points = Arrays.asList(
            new Pt(0, 0),
            new Pt(0, 50),
            new Pt(0, 100)
        );
        Curve2D seam = new Curve2D("TEST_UP", points);
        
        double allowance = 10.0;
        Curve2D offset = generator.generateOffset(seam, allowance, true);
        
        // Check we have the same number of points
        assertEquals(3, offset.getPoints().size());
        
        // For a vertical line going down (0,0 to 0,100), left normal points to negative X
        // So offset should be at x = -10
        for (Pt pt : offset.getPoints()) {
            assertEquals(-10.0, pt.getX(), 0.01, "X coordinate should be offset by -10");
        }
        
        // Y coordinates should match original
        assertEquals(0.0, offset.getPoints().get(0).getY(), 0.01);
        assertEquals(50.0, offset.getPoints().get(1).getY(), 0.01);
        assertEquals(100.0, offset.getPoints().get(2).getY(), 0.01);
        
        // Check ID is correctly generated
        assertEquals("TEST_UP_ALLOW", offset.getId());
    }

    @Test
    public void testSimpleVerticalLineOffsetRight() {
        // Simple vertical line from (0, 0) to (0, 100)
        List<Pt> points = Arrays.asList(
            new Pt(0, 0),
            new Pt(0, 50),
            new Pt(0, 100)
        );
        Curve2D seam = new Curve2D("TEST_UP", points);
        
        double allowance = 10.0;
        Curve2D offset = generator.generateOffset(seam, allowance, false);
        
        // For a vertical line going down, right normal points to positive X
        // So offset should be at x = +10
        for (Pt pt : offset.getPoints()) {
            assertEquals(10.0, pt.getX(), 0.01, "X coordinate should be offset by +10");
        }
    }

    @Test
    public void testOffsetIsNonZero() {
        // Any simple curve with non-zero allowance should produce non-zero offset
        List<Pt> points = Arrays.asList(
            new Pt(10, 20),
            new Pt(10, 30)
        );
        Curve2D seam = new Curve2D("TEST", points);
        
        Curve2D offset = generator.generateOffset(seam, 5.0, true);
        
        // At least one coordinate should differ from original
        boolean hasOffset = false;
        for (int i = 0; i < points.size(); i++) {
            Pt original = points.get(i);
            Pt offsetPt = offset.getPoints().get(i);
            
            if (Math.abs(original.getX() - offsetPt.getX()) > 0.01 ||
                Math.abs(original.getY() - offsetPt.getY()) > 0.01) {
                hasOffset = true;
                break;
            }
        }
        
        assertTrue(hasOffset, "Offset curve should differ from original");
    }

    @Test
    public void testZeroAllowanceProducesSamePoints() {
        List<Pt> points = Arrays.asList(
            new Pt(0, 0),
            new Pt(0, 100)
        );
        Curve2D seam = new Curve2D("TEST", points);
        
        Curve2D offset = generator.generateOffset(seam, 0.0, true);
        
        // With zero allowance, points should be the same
        for (int i = 0; i < points.size(); i++) {
            assertEquals(points.get(i).getX(), offset.getPoints().get(i).getX(), 0.01);
            assertEquals(points.get(i).getY(), offset.getPoints().get(i).getY(), 0.01);
        }
    }

    @Test
    public void testNullSeamThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            generator.generateOffset(null, 10.0, true);
        });
    }

    @Test
    public void testNegativeAllowanceThrowsException() {
        List<Pt> points = Arrays.asList(new Pt(0, 0), new Pt(0, 100));
        Curve2D seam = new Curve2D("TEST", points);
        
        assertThrows(IllegalArgumentException.class, () -> {
            generator.generateOffset(seam, -5.0, true);
        });
    }

    @Test
    public void testSameNumberOfPoints() {
        List<Pt> points = Arrays.asList(
            new Pt(0, 0),
            new Pt(5, 10),
            new Pt(10, 20),
            new Pt(15, 30),
            new Pt(20, 40)
        );
        Curve2D seam = new Curve2D("TEST", points);
        
        Curve2D offset = generator.generateOffset(seam, 10.0, true);
        
        assertEquals(points.size(), offset.getPoints().size(), 
            "Offset curve should have same number of points as original");
    }
}
