package sk.arsi.corset.app;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sk.arsi.corset.model.Curve2D;
import sk.arsi.corset.model.PanelCurves;
import sk.arsi.corset.model.PanelId;
import sk.arsi.corset.model.Pt;
import sk.arsi.corset.resize.PanelResizer;
import sk.arsi.corset.resize.ResizeMode;
import sk.arsi.corset.svg.PathSampler;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for resize workflow including Apply and deep copy functionality.
 */
class ResizeWorkflowTest {

    private PathSampler sampler;
    private PanelResizer resizer;

    @BeforeEach
    void setUp() {
        sampler = new PathSampler();
        resizer = new PanelResizer(sampler, 0.5, 0.0);
    }

    @Test
    void testDeepCopyPanels_CreatesIndependentCopies() {
        List<PanelCurves> original = createTestPanels();
        List<PanelCurves> copy = deepCopyPanels(original);

        // Should be different list
        assertNotSame(original, copy);
        assertEquals(original.size(), copy.size());

        // Panels should be different objects
        for (int i = 0; i < original.size(); i++) {
            PanelCurves origPanel = original.get(i);
            PanelCurves copyPanel = copy.get(i);

            assertNotSame(origPanel, copyPanel);
            assertEquals(origPanel.getPanelId(), copyPanel.getPanelId());

            // Curves should be different objects
            assertNotSame(origPanel.getTop(), copyPanel.getTop());
            assertNotSame(origPanel.getBottom(), copyPanel.getBottom());
            assertNotSame(origPanel.getWaist(), copyPanel.getWaist());

            // But should have same d strings
            assertEquals(origPanel.getTop().getD(), copyPanel.getTop().getD());
            assertEquals(origPanel.getBottom().getD(), copyPanel.getBottom().getD());
            assertEquals(origPanel.getWaist().getD(), copyPanel.getWaist().getD());

            // Points should be different objects but same values
            assertNotSame(origPanel.getTop().getPoints(), copyPanel.getTop().getPoints());
            assertEquals(origPanel.getTop().getPoints().size(), copyPanel.getTop().getPoints().size());
            
            for (int j = 0; j < origPanel.getTop().getPoints().size(); j++) {
                Pt origPt = origPanel.getTop().getPoints().get(j);
                Pt copyPt = copyPanel.getTop().getPoints().get(j);
                
                // Points should be different objects
                assertNotSame(origPt, copyPt);
                
                // But have same coordinates
                assertEquals(origPt.getX(), copyPt.getX(), 0.001);
                assertEquals(origPt.getY(), copyPt.getY(), 0.001);
            }
        }
    }

    @Test
    void testDeepCopyPanels_NullHandling() {
        List<PanelCurves> copy = deepCopyPanels(null);
        assertNull(copy);
    }

    @Test
    void testApplyWorkflow_CombinesResizeModes() {
        // Simulate Apply workflow:
        // 1. Start with original panels
        List<PanelCurves> panelsOriginal = createTestPanels();
        
        // 2. Apply TOP resize (delta = 10mm)
        List<PanelCurves> afterTop = resizer.resize(panelsOriginal, ResizeMode.TOP, 10.0);
        
        // Get the d string of modified top curve after TOP resize
        String topDAfterTopResize = afterTop.get(0).getTop().getD();
        
        // 3. "Apply" - make afterTop the new base
        panelsOriginal = deepCopyPanels(afterTop);
        
        // 4. Apply WAIST resize on top of the TOP-resized base
        List<PanelCurves> afterWaist = resizer.resize(panelsOriginal, ResizeMode.WAIST, 10.0);
        
        // Verify that the top curve in afterWaist still has the TOP resize applied
        // (it should have the same d string as after TOP resize, since WAIST doesn't modify TOP)
        String topDAfterWaistResize = afterWaist.get(0).getTop().getD();
        assertEquals(topDAfterTopResize, topDAfterWaistResize, 
            "Top curve should retain TOP resize modifications after WAIST resize");
        
        // Verify that waist has been modified by WAIST resize
        String waistDOriginal = createTestPanels().get(0).getWaist().getD();
        String waistDAfterWaist = afterWaist.get(0).getWaist().getD();
        assertNotEquals(waistDOriginal, waistDAfterWaist,
            "Waist curve should be modified by WAIST resize");
    }

    @Test
    void testApplyWorkflow_PanelsOriginalUpdated() {
        // Test that Apply correctly updates panelsOriginal
        List<PanelCurves> panelsOriginal = createTestPanels();
        String originalTopD = panelsOriginal.get(0).getTop().getD();
        
        // Apply TOP resize
        List<PanelCurves> afterResize = resizer.resize(panelsOriginal, ResizeMode.TOP, 10.0);
        String resizedTopD = afterResize.get(0).getTop().getD();
        
        // Verify that resize changed the top curve
        assertNotEquals(originalTopD, resizedTopD, 
            "Resize should modify the top curve");
        
        // Simulate Apply: replace panelsOriginal with deep copy of afterResize
        panelsOriginal = deepCopyPanels(afterResize);
        
        // Verify that panelsOriginal now has the resized d string
        assertEquals(resizedTopD, panelsOriginal.get(0).getTop().getD(),
            "After Apply, panelsOriginal should have the resized geometry");
        
        // Verify that subsequent neutral resize returns the updated base
        List<PanelCurves> afterNeutral = resizer.resize(panelsOriginal, ResizeMode.DISABLED, 0.0);
        assertSame(panelsOriginal, afterNeutral, 
            "Neutral resize should return original panels");
        assertEquals(resizedTopD, afterNeutral.get(0).getTop().getD(),
            "Neutral resize should preserve the applied geometry");
    }

    /**
     * Helper method to deep copy panels (mirrors Canvas2DView implementation).
     */
    private List<PanelCurves> deepCopyPanels(List<PanelCurves> source) {
        if (source == null) {
            return null;
        }
        
        List<PanelCurves> copy = new ArrayList<>();
        for (PanelCurves panel : source) {
            PanelCurves copiedPanel = new PanelCurves(
                panel.getPanelId(),
                deepCopyCurve(panel.getTop()),
                deepCopyCurve(panel.getBottom()),
                deepCopyCurve(panel.getWaist()),
                deepCopyCurve(panel.getSeamToPrevUp()),
                deepCopyCurve(panel.getSeamToPrevDown()),
                deepCopyCurve(panel.getSeamToNextUp()),
                deepCopyCurve(panel.getSeamToNextDown())
            );
            copy.add(copiedPanel);
        }
        return copy;
    }

    /**
     * Helper method to deep copy a curve (mirrors Canvas2DView implementation).
     */
    private Curve2D deepCopyCurve(Curve2D source) {
        if (source == null) {
            return null;
        }
        
        List<Pt> copiedPoints = new ArrayList<>();
        for (Pt pt : source.getPoints()) {
            copiedPoints.add(new Pt(pt.getX(), pt.getY()));
        }
        
        return new Curve2D(source.getId(), source.getD(), copiedPoints);
    }

    /**
     * Create simple test panels with SVG path data.
     */
    private List<PanelCurves> createTestPanels() {
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
