package sk.arsi.corset.resize;

import org.junit.jupiter.api.Test;
import sk.arsi.corset.model.Curve2D;
import sk.arsi.corset.model.PanelCurves;
import sk.arsi.corset.model.PanelId;
import sk.arsi.corset.model.Pt;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ResizedPanel, specifically testing TOP mode behavior.
 */
class ResizedPanelTest {

    private PanelCurves createTestPanel() {
        // Create test curves for a panel
        List<Pt> topPoints = Arrays.asList(
            new Pt(0.0, 10.0),    // Left top (minY)
            new Pt(50.0, 20.0),
            new Pt(100.0, 10.0)   // Right top (minY)
        );
        
        List<Pt> bottomPoints = Arrays.asList(
            new Pt(0.0, 200.0),
            new Pt(50.0, 210.0),
            new Pt(100.0, 200.0)
        );
        
        List<Pt> waistPoints = Arrays.asList(
            new Pt(0.0, 100.0),
            new Pt(50.0, 105.0),
            new Pt(100.0, 100.0)
        );
        
        // Left seam curves (seamToPrev)
        List<Pt> seamToPrevUpPoints = Arrays.asList(
            new Pt(5.0, 10.0),    // Top (minY)
            new Pt(5.0, 50.0),
            new Pt(5.0, 100.0)    // Bottom (at waist level)
        );
        
        List<Pt> seamToPrevDownPoints = Arrays.asList(
            new Pt(5.0, 100.0),   // Top (at waist level)
            new Pt(5.0, 150.0),
            new Pt(5.0, 200.0)    // Bottom
        );
        
        // Right seam curves (seamToNext)
        List<Pt> seamToNextUpPoints = Arrays.asList(
            new Pt(95.0, 10.0),   // Top (minY)
            new Pt(95.0, 50.0),
            new Pt(95.0, 100.0)   // Bottom (at waist level)
        );
        
        List<Pt> seamToNextDownPoints = Arrays.asList(
            new Pt(95.0, 100.0),  // Top (at waist level)
            new Pt(95.0, 150.0),
            new Pt(95.0, 200.0)   // Bottom
        );
        
        return new PanelCurves(
            PanelId.A,
            new Curve2D("top", topPoints),
            new Curve2D("bottom", bottomPoints),
            new Curve2D("waist", waistPoints),
            new Curve2D("seamToPrevUp", seamToPrevUpPoints),
            new Curve2D("seamToPrevDown", seamToPrevDownPoints),
            new Curve2D("seamToNextUp", seamToNextUpPoints),
            new Curve2D("seamToNextDown", seamToNextDownPoints)
        );
    }

    @Test
    void testTopMode_waistCurveUnchanged() {
        PanelCurves originalPanel = createTestPanel();
        double sideShift = 5.0;
        
        ResizedPanel resizedPanel = new ResizedPanel(originalPanel, ResizeMode.TOP, sideShift);
        
        Curve2D waist = resizedPanel.getWaist();
        Curve2D originalWaist = originalPanel.getWaist();
        
        // Waist curve should be completely unchanged in TOP mode
        assertNotNull(waist);
        assertEquals(originalWaist.getPoints().size(), waist.getPoints().size());
        
        for (int i = 0; i < originalWaist.getPoints().size(); i++) {
            Pt original = originalWaist.getPoints().get(i);
            Pt resized = waist.getPoints().get(i);
            
            assertEquals(original.getX(), resized.getX(), 0.001,
                "Waist point " + i + " X should be unchanged in TOP mode");
            assertEquals(original.getY(), resized.getY(), 0.001,
                "Waist point " + i + " Y should be unchanged in TOP mode");
        }
    }

    @Test
    void testTopMode_bottomCurveUnchanged() {
        PanelCurves originalPanel = createTestPanel();
        double sideShift = 5.0;
        
        ResizedPanel resizedPanel = new ResizedPanel(originalPanel, ResizeMode.TOP, sideShift);
        
        Curve2D bottom = resizedPanel.getBottom();
        Curve2D originalBottom = originalPanel.getBottom();
        
        // Bottom curve should be completely unchanged in TOP mode
        assertNotNull(bottom);
        assertEquals(originalBottom.getPoints().size(), bottom.getPoints().size());
        
        for (int i = 0; i < originalBottom.getPoints().size(); i++) {
            Pt original = originalBottom.getPoints().get(i);
            Pt resized = bottom.getPoints().get(i);
            
            assertEquals(original.getX(), resized.getX(), 0.001,
                "Bottom point " + i + " X should be unchanged in TOP mode");
            assertEquals(original.getY(), resized.getY(), 0.001,
                "Bottom point " + i + " Y should be unchanged in TOP mode");
        }
    }

    @Test
    void testTopMode_downSeamsUnchanged() {
        PanelCurves originalPanel = createTestPanel();
        double sideShift = 5.0;
        
        ResizedPanel resizedPanel = new ResizedPanel(originalPanel, ResizeMode.TOP, sideShift);
        
        // Check seamToPrevDown
        Curve2D seamToPrevDown = resizedPanel.getSeamToPrevDown();
        Curve2D originalSeamToPrevDown = originalPanel.getSeamToPrevDown();
        
        for (int i = 0; i < originalSeamToPrevDown.getPoints().size(); i++) {
            Pt original = originalSeamToPrevDown.getPoints().get(i);
            Pt resized = seamToPrevDown.getPoints().get(i);
            
            assertEquals(original.getX(), resized.getX(), 0.001,
                "SeamToPrevDown point " + i + " should be unchanged in TOP mode");
            assertEquals(original.getY(), resized.getY(), 0.001);
        }
        
        // Check seamToNextDown
        Curve2D seamToNextDown = resizedPanel.getSeamToNextDown();
        Curve2D originalSeamToNextDown = originalPanel.getSeamToNextDown();
        
        for (int i = 0; i < originalSeamToNextDown.getPoints().size(); i++) {
            Pt original = originalSeamToNextDown.getPoints().get(i);
            Pt resized = seamToNextDown.getPoints().get(i);
            
            assertEquals(original.getX(), resized.getX(), 0.001,
                "SeamToNextDown point " + i + " should be unchanged in TOP mode");
            assertEquals(original.getY(), resized.getY(), 0.001);
        }
    }

    @Test
    void testTopMode_onlyMinYPointOfSeamsShifted() {
        PanelCurves originalPanel = createTestPanel();
        double sideShift = 5.0;
        
        ResizedPanel resizedPanel = new ResizedPanel(originalPanel, ResizeMode.TOP, sideShift);
        
        // Check seamToPrevUp - left seam should shift left (negative)
        Curve2D seamToPrevUp = resizedPanel.getSeamToPrevUp();
        List<Pt> seamToPrevUpPoints = seamToPrevUp.getPoints();
        
        // First point is minY (y=10), original X=5.0, should be shifted by -sideShift (-5.0) to X=0.0
        assertEquals(5.0 - sideShift, seamToPrevUpPoints.get(0).getX(), 0.001, 
            "Left seam top point should shift from 5.0 to 0.0 (5.0 - 5.0)");
        assertEquals(10.0, seamToPrevUpPoints.get(0).getY(), 0.001);
        
        // Other points should be unchanged
        assertEquals(5.0, seamToPrevUpPoints.get(1).getX(), 0.001);
        assertEquals(5.0, seamToPrevUpPoints.get(2).getX(), 0.001);
        
        // Check seamToNextUp - right seam should shift right (positive)
        Curve2D seamToNextUp = resizedPanel.getSeamToNextUp();
        List<Pt> seamToNextUpPoints = seamToNextUp.getPoints();
        
        // First point is minY (y=10), original X=95.0, should be shifted by +sideShift (+5.0) to X=100.0
        assertEquals(95.0 + sideShift, seamToNextUpPoints.get(0).getX(), 0.001,
            "Right seam top point should shift from 95.0 to 100.0 (95.0 + 5.0)");
        assertEquals(10.0, seamToNextUpPoints.get(0).getY(), 0.001);
        
        // Other points should be unchanged
        assertEquals(95.0, seamToNextUpPoints.get(1).getX(), 0.001);
        assertEquals(95.0, seamToNextUpPoints.get(2).getX(), 0.001);
    }

    @Test
    void testTopMode_onlyTopEndpointsOfTopCurveShifted() {
        PanelCurves originalPanel = createTestPanel();
        double sideShift = 5.0;
        
        ResizedPanel resizedPanel = new ResizedPanel(originalPanel, ResizeMode.TOP, sideShift);
        
        Curve2D top = resizedPanel.getTop();
        List<Pt> topPoints = top.getPoints();
        
        assertEquals(3, topPoints.size());
        
        // Left endpoint (minY, minX) should shift by -sideShift
        assertEquals(-5.0, topPoints.get(0).getX(), 0.001,
            "Left top endpoint should shift by -sideShift");
        assertEquals(10.0, topPoints.get(0).getY(), 0.001);
        
        // Middle point should be unchanged
        assertEquals(50.0, topPoints.get(1).getX(), 0.001,
            "Middle point of top curve should be unchanged");
        assertEquals(20.0, topPoints.get(1).getY(), 0.001);
        
        // Right endpoint (minY, maxX) should shift by +sideShift
        assertEquals(105.0, topPoints.get(2).getX(), 0.001,
            "Right top endpoint should shift by +sideShift");
        assertEquals(10.0, topPoints.get(2).getY(), 0.001);
    }

    @Test
    void testTopMode_negativeShift() {
        // Test shrinking (negative delta) in TOP mode
        PanelCurves originalPanel = createTestPanel();
        double sideShift = -3.0;
        
        ResizedPanel resizedPanel = new ResizedPanel(originalPanel, ResizeMode.TOP, sideShift);
        
        Curve2D top = resizedPanel.getTop();
        List<Pt> topPoints = top.getPoints();
        
        // With negative shift, left endpoint moves right, right endpoint moves left
        assertEquals(3.0, topPoints.get(0).getX(), 0.001,
            "Left endpoint shifts right with negative shift");
        assertEquals(97.0, topPoints.get(2).getX(), 0.001,
            "Right endpoint shifts left with negative shift");
        
        // Seams should also respond to negative shift
        Curve2D seamToPrevUp = resizedPanel.getSeamToPrevUp();
        assertEquals(8.0, seamToPrevUp.getPoints().get(0).getX(), 0.001,
            "Left seam moves right with negative shift");
        
        Curve2D seamToNextUp = resizedPanel.getSeamToNextUp();
        assertEquals(92.0, seamToNextUp.getPoints().get(0).getX(), 0.001,
            "Right seam moves left with negative shift");
    }

    @Test
    void testGlobalMode_stillWorks() {
        // Ensure GLOBAL mode still works after TOP mode changes
        PanelCurves originalPanel = createTestPanel();
        double sideShift = 4.0;
        
        ResizedPanel resizedPanel = new ResizedPanel(originalPanel, ResizeMode.GLOBAL, sideShift);
        
        // In GLOBAL mode, all curves should be resized
        Curve2D top = resizedPanel.getTop();
        Curve2D bottom = resizedPanel.getBottom();
        Curve2D waist = resizedPanel.getWaist();
        
        assertNotNull(top);
        assertNotNull(bottom);
        assertNotNull(waist);
        
        // Verify that curves are actually different from original
        assertNotEquals(originalPanel.getTop().getPoints().get(0).getX(),
            top.getPoints().get(0).getX(), 0.001,
            "GLOBAL mode should modify top curve");
    }
}
