package sk.arsi.corset.resize;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sk.arsi.corset.model.Curve2D;
import sk.arsi.corset.model.PanelCurves;
import sk.arsi.corset.model.PanelId;
import sk.arsi.corset.model.Pt;
import sk.arsi.corset.svg.PathSampler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PanelResizerTest {

    private PathSampler sampler;
    private PanelResizer resizer;

    @BeforeEach
    void setUp() {
        sampler = new PathSampler();
        resizer = new PanelResizer(sampler, 0.5, 0.0);
    }

    @Test
    void testDisabledMode_ReturnsOriginals() {
        List<PanelCurves> originalPanels = createTestPanels();
        
        List<PanelCurves> resized = resizer.resize(originalPanels, ResizeMode.DISABLED, 10.0);
        
        // Should return the same list (not modified)
        assertSame(originalPanels, resized);
    }

    @Test
    void testZeroDelta_ReturnsOriginals() {
        List<PanelCurves> originalPanels = createTestPanels();
        
        List<PanelCurves> resized = resizer.resize(originalPanels, ResizeMode.TOP, 0.0);
        
        assertSame(originalPanels, resized);
    }

    @Test
    void testTopMode_OnlyModifiesTopAndUpSeams() {
        List<PanelCurves> originalPanels = createTestPanels();
        PanelCurves originalPanel = originalPanels.get(0);
        
        List<PanelCurves> resized = resizer.resize(originalPanels, ResizeMode.TOP, 10.0);
        
        PanelCurves resizedPanel = resized.get(0);
        
        // Top, seamToPrevUp, seamToNextUp should have different d strings (modified)
        assertNotNull(resizedPanel.getTop().getD());
        assertNotNull(resizedPanel.getSeamToPrevUp().getD());
        assertNotNull(resizedPanel.getSeamToNextUp().getD());
        
        // Waist, bottom, and DOWN seams should be the same objects (unchanged)
        assertSame(originalPanel.getWaist(), resizedPanel.getWaist());
        assertSame(originalPanel.getBottom(), resizedPanel.getBottom());
        assertSame(originalPanel.getSeamToPrevDown(), resizedPanel.getSeamToPrevDown());
        assertSame(originalPanel.getSeamToNextDown(), resizedPanel.getSeamToNextDown());
    }

    @Test
    void testTopMode_KeepsOriginalDStrings() {
        List<PanelCurves> originalPanels = createTestPanels();
        PanelCurves originalPanel = originalPanels.get(0);
        
        String originalWaistD = originalPanel.getWaist().getD();
        String originalBottomD = originalPanel.getBottom().getD();
        String originalSeamDownPrevD = originalPanel.getSeamToPrevDown().getD();
        String originalSeamDownNextD = originalPanel.getSeamToNextDown().getD();
        
        List<PanelCurves> resized = resizer.resize(originalPanels, ResizeMode.TOP, 10.0);
        PanelCurves resizedPanel = resized.get(0);
        
        // D strings should be exactly the same for unchanged curves
        assertEquals(originalWaistD, resizedPanel.getWaist().getD());
        assertEquals(originalBottomD, resizedPanel.getBottom().getD());
        assertEquals(originalSeamDownPrevD, resizedPanel.getSeamToPrevDown().getD());
        assertEquals(originalSeamDownNextD, resizedPanel.getSeamToNextDown().getD());
    }

    @Test
    void testGlobalMode_ModifiesAllCurves() {
        List<PanelCurves> originalPanels = createTestPanels();
        PanelCurves originalPanel = originalPanels.get(0);
        
        List<PanelCurves> resized = resizer.resize(originalPanels, ResizeMode.GLOBAL, 10.0);
        
        PanelCurves resizedPanel = resized.get(0);
        
        // All curves should be different objects (modified)
        assertNotSame(originalPanel.getTop(), resizedPanel.getTop());
        assertNotSame(originalPanel.getBottom(), resizedPanel.getBottom());
        assertNotSame(originalPanel.getWaist(), resizedPanel.getWaist());
        assertNotSame(originalPanel.getSeamToPrevUp(), resizedPanel.getSeamToPrevUp());
        assertNotSame(originalPanel.getSeamToPrevDown(), resizedPanel.getSeamToPrevDown());
        assertNotSame(originalPanel.getSeamToNextUp(), resizedPanel.getSeamToNextUp());
        assertNotSame(originalPanel.getSeamToNextDown(), resizedPanel.getSeamToNextDown());
    }

    @Test
    void testTopMode_TopEdgeEndpointsMoved() {
        List<PanelCurves> originalPanels = createTestPanels();
        PanelCurves originalPanel = originalPanels.get(0);
        
        // For 1 panel, deltaMm=10, sideShiftMm = 10/(4*1) = 2.5
        List<PanelCurves> resized = resizer.resize(originalPanels, ResizeMode.TOP, 10.0);
        PanelCurves resizedPanel = resized.get(0);
        
        // Get original and resized top edge points
        List<Pt> originalTopPoints = originalPanel.getTop().getPoints();
        List<Pt> resizedTopPoints = resizedPanel.getTop().getPoints();
        
        // Original top edge is "M 0 0 L 100 0", so left endpoint is (0,0), right is (100,0)
        // After resize with sideShift=2.5: left should move to (-2.5, 0), right to (102.5, 0)
        
        // Find leftmost and rightmost points in resized top
        Pt resizedLeft = resizedTopPoints.stream()
            .min((a, b) -> Double.compare(a.getX(), b.getX()))
            .orElseThrow();
        Pt resizedRight = resizedTopPoints.stream()
            .max((a, b) -> Double.compare(a.getX(), b.getX()))
            .orElseThrow();
        
        // Verify the X coordinates have shifted by approximately ±2.5mm
        assertEquals(-2.5, resizedLeft.getX(), 0.1, "Left endpoint X should shift by -2.5mm");
        assertEquals(102.5, resizedRight.getX(), 0.1, "Right endpoint X should shift by +2.5mm");
        
        // Verify Y coordinate unchanged
        assertEquals(0.0, resizedLeft.getY(), 0.1, "Left endpoint Y should remain at 0");
        assertEquals(0.0, resizedRight.getY(), 0.1, "Right endpoint Y should remain at 0");
    }

    @Test
    void testTopMode_UpSeamEndpointsMoved() {
        List<PanelCurves> originalPanels = createTestPanels();
        
        // For 1 panel, deltaMm=10, sideShiftMm = 10/(4*1) = 2.5
        List<PanelCurves> resized = resizer.resize(originalPanels, ResizeMode.TOP, 10.0);
        PanelCurves resizedPanel = resized.get(0);
        
        // seamToPrevUp starts as a vertical line "M 0 0 L 0 50" from (0,0) to (0,50)
        // The topmost point (minY) at (0, 0) should shift by -2.5 in X to (-2.5, 0)
        List<Pt> prevUpPoints = resizedPanel.getSeamToPrevUp().getPoints();
        Pt prevUpTop = prevUpPoints.stream()
            .min((a, b) -> Double.compare(a.getY(), b.getY()))
            .orElseThrow();
        
        assertEquals(-2.5, prevUpTop.getX(), 0.1, "PrevUp top endpoint X should shift by -2.5mm");
        assertEquals(0.0, prevUpTop.getY(), 0.1, "PrevUp top endpoint Y should remain at 0");
        
        // seamToNextUp also starts as a vertical line "M 0 0 L 0 50" from (0,0) to (0,50)
        // The topmost point (minY) at (0, 0) should shift by +2.5 in X to (2.5, 0)
        List<Pt> nextUpPoints = resizedPanel.getSeamToNextUp().getPoints();
        Pt nextUpTop = nextUpPoints.stream()
            .min((a, b) -> Double.compare(a.getY(), b.getY()))
            .orElseThrow();
        
        assertEquals(2.5, nextUpTop.getX(), 0.1, "NextUp top endpoint X should shift by +2.5mm");
        assertEquals(0.0, nextUpTop.getY(), 0.1, "NextUp top endpoint Y should remain at 0");
    }

    @Test
    void testTopMode_WithCurvedPath() {
        // Test with a curved path (cubic bezier) to ensure endpoint modification works
        String curvedTopD = "M 0 0 C 30 -10 70 -10 100 0";
        String seamUpD = "M 0 0 L 0 50";
        
        Curve2D top = sampler.samplePath("top", curvedTopD, 0.5);
        Curve2D bottom = sampler.samplePath("bottom", "M 0 100 L 100 100", 0.5);
        Curve2D waist = sampler.samplePath("waist", "M 0 50 L 100 50", 0.5);
        Curve2D seamToPrevUp = sampler.samplePath("seamPrevUp", seamUpD, 0.5);
        Curve2D seamToPrevDown = sampler.samplePath("seamPrevDown", "M 0 50 L 0 100", 0.5);
        Curve2D seamToNextUp = sampler.samplePath("seamNextUp", seamUpD, 0.5);
        Curve2D seamToNextDown = sampler.samplePath("seamNextDown", "M 0 50 L 0 100", 0.5);
        
        PanelCurves panel = new PanelCurves(
            PanelId.A,
            top, bottom, waist,
            seamToPrevUp, seamToPrevDown,
            seamToNextUp, seamToNextDown
        );
        
        List<PanelCurves> panels = new ArrayList<>();
        panels.add(panel);
        
        // Get original X range
        double originalMinX = top.getPoints().stream().mapToDouble(Pt::getX).min().orElse(0);
        double originalMaxX = top.getPoints().stream().mapToDouble(Pt::getX).max().orElse(0);
        
        // For 1 panel, deltaMm=10, sideShiftMm = 10/(4*1) = 2.5
        List<PanelCurves> resized = resizer.resize(panels, ResizeMode.TOP, 10.0);
        PanelCurves resizedPanel = resized.get(0);
        
        // Check that the top edge endpoints have shifted
        double resizedMinX = resizedPanel.getTop().getPoints().stream().mapToDouble(Pt::getX).min().orElse(0);
        double resizedMaxX = resizedPanel.getTop().getPoints().stream().mapToDouble(Pt::getX).max().orElse(0);
        
        // Left endpoint should shift by -2.5, right by +2.5
        assertEquals(originalMinX - 2.5, resizedMinX, 0.5, "Left endpoint should shift by -2.5mm");
        assertEquals(originalMaxX + 2.5, resizedMaxX, 0.5, "Right endpoint should shift by +2.5mm");
    }

    private List<PanelCurves> createTestPanels() {
        // Create simple test panel with SVG path data
        String topD = "M 0 0 L 100 0";
        String bottomD = "M 0 100 L 100 100";
        String waistD = "M 0 50 L 100 50";
        String seamUpD = "M 0 0 L 0 50";
        String seamDownD = "M 0 50 L 0 100";

        Curve2D top = sampler.samplePath("top", topD, 0.5);
        Curve2D bottom = sampler.samplePath("bottom", bottomD, 0.5);
        Curve2D waist = sampler.samplePath("waist", waistD, 0.5);
        Curve2D seamToPrevUp = sampler.samplePath("seamPrevUp", seamUpD, 0.5);
        Curve2D seamToPrevDown = sampler.samplePath("seamPrevDown", seamDownD, 0.5);
        Curve2D seamToNextUp = sampler.samplePath("seamNextUp", seamUpD, 0.5);
        Curve2D seamToNextDown = sampler.samplePath("seamNextDown", seamDownD, 0.5);

        PanelCurves panel = new PanelCurves(
            PanelId.A,
            top, bottom, waist,
            seamToPrevUp, seamToPrevDown,
            seamToNextUp, seamToNextDown
        );

        List<PanelCurves> panels = new ArrayList<>();
        panels.add(panel);
        return panels;
    }
}
