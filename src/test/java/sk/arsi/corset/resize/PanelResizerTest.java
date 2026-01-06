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
