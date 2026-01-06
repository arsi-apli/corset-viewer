package sk.arsi.corset.model;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class Curve2DTest {

    @Test
    void testConstructorWithD() {
        String id = "test-curve";
        String d = "M 0 0 L 10 10";
        List<Pt> points = Arrays.asList(new Pt(0, 0), new Pt(10, 10));
        
        Curve2D curve = new Curve2D(id, d, points);
        
        assertEquals(id, curve.getId());
        assertEquals(d, curve.getD());
        assertEquals(points, curve.getPoints());
    }

    @Test
    void testBackwardCompatibleConstructor() {
        String id = "synthetic-curve";
        List<Pt> points = Arrays.asList(new Pt(0, 0), new Pt(10, 10));
        
        Curve2D curve = new Curve2D(id, points);
        
        assertEquals(id, curve.getId());
        assertNull(curve.getD()); // d should be null for synthetic curves
        assertEquals(points, curve.getPoints());
    }

    @Test
    void testGetFirst() {
        List<Pt> points = Arrays.asList(new Pt(0, 0), new Pt(10, 10), new Pt(20, 20));
        Curve2D curve = new Curve2D("test", "M 0 0 L 10 10 L 20 20", points);
        
        assertEquals(0.0, curve.getFirst().getX(), 1e-6);
        assertEquals(0.0, curve.getFirst().getY(), 1e-6);
    }

    @Test
    void testGetLast() {
        List<Pt> points = Arrays.asList(new Pt(0, 0), new Pt(10, 10), new Pt(20, 20));
        Curve2D curve = new Curve2D("test", "M 0 0 L 10 10 L 20 20", points);
        
        assertEquals(20.0, curve.getLast().getX(), 1e-6);
        assertEquals(20.0, curve.getLast().getY(), 1e-6);
    }

    @Test
    void testNullDIsAllowed() {
        List<Pt> points = Arrays.asList(new Pt(0, 0), new Pt(10, 10));
        Curve2D curve = new Curve2D("test", null, points);
        
        assertNull(curve.getD());
    }
}
