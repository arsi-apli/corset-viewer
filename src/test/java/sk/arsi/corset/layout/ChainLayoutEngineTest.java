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

    @Test
    void testSeamEndpointSnapping_twoPanels() {
        ChainLayoutEngine engine = new ChainLayoutEngine();
        
        // Create two panels with seam curves
        PanelCurves panelA = createPanelWithSeams(PanelId.A, 0.0, 100.0);
        PanelCurves panelB = createPanelWithSeams(PanelId.B, 0.0, 100.0);
        List<PanelCurves> panels = Arrays.asList(panelA, panelB);

        List<LayoutResult> results = engine.computeLayout(panels, EdgeMode.BOTTOM);

        assertEquals(2, results.size(), "Expected two layout results");
        
        // Get transforms
        Transform2D transformA = results.get(0).getTransform();
        Transform2D transformB = results.get(1).getTransform();
        
        // Verify seam endpoints are aligned
        // Panel A's seamToNext endpoint should align with Panel B's seamToPrev endpoint
        Pt seamAEndpoint = transformA.apply(panelA.getSeamToNextDown().getLast());
        Pt seamBEndpoint = transformB.apply(panelB.getSeamToPrevDown().getLast());
        
        assertNotNull(seamAEndpoint, "Panel A seam endpoint should not be null");
        assertNotNull(seamBEndpoint, "Panel B seam endpoint should not be null");
        
        // The endpoints should be very close (within tolerance)
        assertEquals(seamAEndpoint.getX(), seamBEndpoint.getX(), 0.001,
            "Seam endpoints should align in X coordinate");
        assertEquals(seamAEndpoint.getY(), seamBEndpoint.getY(), 0.001,
            "Seam endpoints should align in Y coordinate");
    }

    @Test
    void testSeamEndpointSnapping_topMode() {
        ChainLayoutEngine engine = new ChainLayoutEngine();
        
        // Create two panels with seam curves
        PanelCurves panelA = createPanelWithSeams(PanelId.A, 0.0, 100.0);
        PanelCurves panelB = createPanelWithSeams(PanelId.B, 0.0, 100.0);
        List<PanelCurves> panels = Arrays.asList(panelA, panelB);

        List<LayoutResult> results = engine.computeLayout(panels, EdgeMode.TOP);

        assertEquals(2, results.size(), "Expected two layout results");
        
        // Get transforms
        Transform2D transformA = results.get(0).getTransform();
        Transform2D transformB = results.get(1).getTransform();
        
        // Verify seam endpoints are aligned using UP seams in TOP mode
        Pt seamAEndpoint = transformA.apply(panelA.getSeamToNextUp().getLast());
        Pt seamBEndpoint = transformB.apply(panelB.getSeamToPrevUp().getLast());
        
        assertNotNull(seamAEndpoint, "Panel A seam endpoint should not be null");
        assertNotNull(seamBEndpoint, "Panel B seam endpoint should not be null");
        
        // The endpoints should be very close (within tolerance)
        assertEquals(seamAEndpoint.getX(), seamBEndpoint.getX(), 0.001,
            "Seam endpoints should align in X coordinate");
        assertEquals(seamAEndpoint.getY(), seamBEndpoint.getY(), 0.001,
            "Seam endpoints should align in Y coordinate");
    }

    @Test
    void testTransform_withAdditionalTranslation() {
        Transform2D original = new Transform2D(Math.PI / 4, 10.0, 20.0, 5.0, 10.0);
        Transform2D modified = original.withAdditionalTranslation(3.0, 7.0);
        
        assertEquals(original.getAngleRad(), modified.getAngleRad(), 0.001,
            "Angle should remain unchanged");
        assertEquals(original.getPivotX(), modified.getPivotX(), 0.001,
            "Pivot X should remain unchanged");
        assertEquals(original.getPivotY(), modified.getPivotY(), 0.001,
            "Pivot Y should remain unchanged");
        assertEquals(original.getTx() + 3.0, modified.getTx(), 0.001,
            "Translation X should be incremented");
        assertEquals(original.getTy() + 7.0, modified.getTy(), 0.001,
            "Translation Y should be incremented");
    }

    private PanelCurves createPanelWithSeams(PanelId id, double xStart, double xEnd) {
        // Create panel with proper seam curves that have endpoints
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
        
        // Seam curves with meaningful endpoints
        Curve2D seamToPrevUp = new Curve2D(
            id.name() + "_SEAM_PREV_UP",
            Arrays.asList(new Pt(xStart, 50.0), new Pt(xStart, 100.0), new Pt(xStart, 125.0))
        );
        
        Curve2D seamToPrevDown = new Curve2D(
            id.name() + "_SEAM_PREV_DOWN",
            Arrays.asList(new Pt(xStart, 125.0), new Pt(xStart, 150.0), new Pt(xStart, 200.0))
        );
        
        Curve2D seamToNextUp = new Curve2D(
            id.name() + "_SEAM_NEXT_UP",
            Arrays.asList(new Pt(xEnd, 50.0), new Pt(xEnd, 100.0), new Pt(xEnd, 125.0))
        );
        
        Curve2D seamToNextDown = new Curve2D(
            id.name() + "_SEAM_NEXT_DOWN",
            Arrays.asList(new Pt(xEnd, 125.0), new Pt(xEnd, 150.0), new Pt(xEnd, 200.0))
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
