package sk.arsi.corset.layout;

import org.junit.jupiter.api.Test;
import sk.arsi.corset.layout.ChainLayoutEngine.EdgeMode;
import sk.arsi.corset.layout.ChainLayoutEngine.LayoutResult;
import sk.arsi.corset.layout.ChainLayoutEngine.Transform2D;
import sk.arsi.corset.model.Curve2D;
import sk.arsi.corset.model.PanelCurves;
import sk.arsi.corset.model.PanelId;
import sk.arsi.corset.model.Pt;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChainLayoutEngineTest {

    @Test
    void testComputeLayout_singlePanel() {
        ChainLayoutEngine engine = new ChainLayoutEngine();
        PanelCurves panel = createSimplePanel(PanelId.A, 0.0, 150.0);
        List<PanelCurves> panels = Arrays.asList(panel);

        List<LayoutResult> results = engine.computeLayout(panels, EdgeMode.TOP);

        assertEquals(1, results.size(), "Expected one layout result");
        
        LayoutResult result = results.get(0);
        assertEquals(PanelId.A, result.getPanel().getPanelId());
        
        // First panel should be translated so waistLeft is at (0, 0)
        Transform2D transform = result.getTransform();
        Pt waistLeft = panel.getWaist().getPoints().get(0);
        Pt transformed = transform.apply(waistLeft);
        
        assertEquals(0.0, transformed.getX(), 0.001, "WaistLeft X should be at 0");
        assertEquals(0.0, transformed.getY(), 0.001, "WaistLeft Y should be at 0");
    }

    @Test
    void testComputeLayout_twoPanels() {
        ChainLayoutEngine engine = new ChainLayoutEngine();
        PanelCurves panelA = createSimplePanel(PanelId.A, 0.0, 150.0);
        PanelCurves panelB = createSimplePanel(PanelId.B, 200.0, 350.0);
        List<PanelCurves> panels = Arrays.asList(panelA, panelB);

        List<LayoutResult> results = engine.computeLayout(panels, EdgeMode.TOP);

        assertEquals(2, results.size(), "Expected two layout results");
        assertNotNull(results.get(0).getTransform());
        assertNotNull(results.get(1).getTransform());
    }

    @Test
    void testComputeLayout_emptyList() {
        ChainLayoutEngine engine = new ChainLayoutEngine();
        List<LayoutResult> results = engine.computeLayout(Arrays.asList(), EdgeMode.TOP);

        assertEquals(0, results.size(), "Expected no layout results for empty list");
    }

    @Test
    void testComputeLayout_bottomEdge() {
        ChainLayoutEngine engine = new ChainLayoutEngine();
        PanelCurves panel = createSimplePanel(PanelId.A, 0.0, 150.0);
        List<PanelCurves> panels = Arrays.asList(panel);

        List<LayoutResult> results = engine.computeLayout(panels, EdgeMode.BOTTOM);

        assertEquals(1, results.size(), "Expected one layout result");
        assertNotNull(results.get(0).getTransform());
    }

    @Test
    void testTransform2D_apply() {
        // Test simple translation
        Transform2D transform = new Transform2D(0.0, 0.0, 0.0, 10.0, 20.0);
        Pt original = new Pt(5.0, 10.0);
        Pt transformed = transform.apply(original);

        assertEquals(15.0, transformed.getX(), 0.001);
        assertEquals(30.0, transformed.getY(), 0.001);
    }

    @Test
    void testTransform2D_applyNull() {
        Transform2D transform = new Transform2D(0.0, 0.0, 0.0, 10.0, 20.0);
        Pt result = transform.apply(null);

        assertNull(result, "Transform should return null for null input");
    }

    private PanelCurves createSimplePanel(PanelId id, double xStart, double xEnd) {
        // Create simple rectangular panel
        Curve2D top = new Curve2D(
            id.name() + "_TOP",
            Arrays.asList(new Pt(xStart, 50.0), new Pt(xEnd, 50.0))
        );
        
        Curve2D bottom = new Curve2D(
            id.name() + "_BOTTOM",
            Arrays.asList(new Pt(xStart, 200.0), new Pt(xEnd, 200.0))
        );
        
        Curve2D waist = new Curve2D(
            id.name() + "_WAIST",
            Arrays.asList(new Pt(xStart, 125.0), new Pt(xEnd, 125.0))
        );
        
        Curve2D seamToPrevUp = new Curve2D(
            id.name() + "_SEAM_PREV_UP",
            Arrays.asList(new Pt(xStart, 50.0), new Pt(xStart, 125.0))
        );
        
        Curve2D seamToPrevDown = new Curve2D(
            id.name() + "_SEAM_PREV_DOWN",
            Arrays.asList(new Pt(xStart, 125.0), new Pt(xStart, 200.0))
        );
        
        Curve2D seamToNextUp = new Curve2D(
            id.name() + "_SEAM_NEXT_UP",
            Arrays.asList(new Pt(xEnd, 50.0), new Pt(xEnd, 125.0))
        );
        
        Curve2D seamToNextDown = new Curve2D(
            id.name() + "_SEAM_NEXT_DOWN",
            Arrays.asList(new Pt(xEnd, 125.0), new Pt(xEnd, 200.0))
        );
        
        return new PanelCurves(
            id,
            top,
            bottom,
            waist,
            seamToPrevUp,
            seamToPrevDown,
            seamToNextUp,
            seamToNextDown
        );
    }
}
