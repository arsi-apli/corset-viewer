package sk.arsi.corset.resize;

import org.junit.jupiter.api.Test;
import sk.arsi.corset.model.Pt;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SvgPathEditorTest {

    @Test
    void testExtractEndpoints_SimplePath() {
        String d = "M 10 20 L 30 40 L 50 60";
        List<Pt> endpoints = SvgPathEditor.extractEndpoints(d);
        
        assertEquals(3, endpoints.size());
        assertEquals(10.0, endpoints.get(0).getX(), 1e-6);
        assertEquals(20.0, endpoints.get(0).getY(), 1e-6);
        assertEquals(30.0, endpoints.get(1).getX(), 1e-6);
        assertEquals(40.0, endpoints.get(1).getY(), 1e-6);
        assertEquals(50.0, endpoints.get(2).getX(), 1e-6);
        assertEquals(60.0, endpoints.get(2).getY(), 1e-6);
    }

    @Test
    void testExtractEndpoints_CubicBezier() {
        // C has 3 points: control1, control2, endpoint
        String d = "M 0 0 C 10 10 20 20 30 30";
        List<Pt> endpoints = SvgPathEditor.extractEndpoints(d);
        
        assertEquals(2, endpoints.size());
        assertEquals(0.0, endpoints.get(0).getX(), 1e-6);
        assertEquals(0.0, endpoints.get(0).getY(), 1e-6);
        assertEquals(30.0, endpoints.get(1).getX(), 1e-6);
        assertEquals(30.0, endpoints.get(1).getY(), 1e-6);
    }

    @Test
    void testModifyEndpoint() {
        String d = "M 10 20 L 30 40 L 50 60";
        
        // Modify second endpoint (index 1)
        String modified = SvgPathEditor.modifyEndpoint(d, 1, 5.0, -3.0);
        
        List<Pt> endpoints = SvgPathEditor.extractEndpoints(modified);
        assertEquals(3, endpoints.size());
        
        // First endpoint unchanged
        assertEquals(10.0, endpoints.get(0).getX(), 1e-6);
        assertEquals(20.0, endpoints.get(0).getY(), 1e-6);
        
        // Second endpoint modified
        assertEquals(35.0, endpoints.get(1).getX(), 1e-6);
        assertEquals(37.0, endpoints.get(1).getY(), 1e-6);
        
        // Third endpoint unchanged
        assertEquals(50.0, endpoints.get(2).getX(), 1e-6);
        assertEquals(60.0, endpoints.get(2).getY(), 1e-6);
    }

    @Test
    void testFindMinYEndpoint() {
        String d = "M 10 50 L 30 20 L 50 40";
        int minIndex = SvgPathEditor.findMinYEndpoint(d);
        
        assertEquals(1, minIndex); // Second point has Y=20 (minimum)
    }

    @Test
    void testFindMaxYEndpoint() {
        String d = "M 10 50 L 30 20 L 50 40";
        int maxIndex = SvgPathEditor.findMaxYEndpoint(d);
        
        assertEquals(0, maxIndex); // First point has Y=50 (maximum)
    }

    @Test
    void testFindLeftRightEndpoints() {
        String d = "M 50 10 L 20 30 L 80 20";
        int[] indices = SvgPathEditor.findLeftRightEndpoints(d);
        
        assertEquals(1, indices[0]); // Left: X=20
        assertEquals(2, indices[1]); // Right: X=80
    }

    @Test
    void testFindTopEdgeEndpoints() {
        // Two points at Y=10 (minimum), at X=20 and X=60
        String d = "M 20 10 L 40 30 L 60 10 L 80 50";
        int[] indices = SvgPathEditor.findTopEdgeEndpoints(d);
        
        assertEquals(0, indices[0]); // Left top: (20, 10)
        assertEquals(2, indices[1]); // Right top: (60, 10)
    }
}
