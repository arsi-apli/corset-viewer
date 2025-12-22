package sk.arsi.corset.layout;

import org.junit.jupiter.api.Test;
import sk.arsi.corset.model.Curve2D;
import sk.arsi.corset.model.PanelCurves;
import sk.arsi.corset.model.PanelId;
import sk.arsi.corset.model.Pt;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PanelOrderDetectorTest {

    @Test
    void testDetectOrderAtoF_whenABtoRightOfAA() {
        // Create panel A with AA seam at x=50 and AB seam at x=200
        Curve2D seamAA = createCurve("AA_UP", 50.0);
        Curve2D seamAB = createCurve("AB_UP", 200.0);
        
        PanelCurves panelA = createPanel(PanelId.A, seamAA, seamAB);
        List<PanelCurves> panels = Arrays.asList(panelA);

        PanelOrderDetector detector = new PanelOrderDetector();
        boolean orderAtoF = detector.detectOrderAtoF(panels);

        assertTrue(orderAtoF, "Expected A→F order when AB is to the right of AA");
    }

    @Test
    void testDetectOrderAtoF_whenAAtoRightOfAB() {
        // Create panel A with AB seam at x=50 and AA seam at x=200
        Curve2D seamAA = createCurve("AA_UP", 200.0);
        Curve2D seamAB = createCurve("AB_UP", 50.0);
        
        PanelCurves panelA = createPanel(PanelId.A, seamAA, seamAB);
        List<PanelCurves> panels = Arrays.asList(panelA);

        PanelOrderDetector detector = new PanelOrderDetector();
        boolean orderAtoF = detector.detectOrderAtoF(panels);

        assertFalse(orderAtoF, "Expected F→A order when AA is to the right of AB");
    }

    @Test
    void testDetectOrderAtoF_emptyPanels() {
        PanelOrderDetector detector = new PanelOrderDetector();
        boolean orderAtoF = detector.detectOrderAtoF(Arrays.asList());

        assertTrue(orderAtoF, "Expected default A→F order for empty panel list");
    }

    @Test
    void testDetectOrderAtoF_noPanelA() {
        PanelCurves panelB = createPanel(PanelId.B, createCurve("BA_UP", 50.0), createCurve("BC_UP", 200.0));
        List<PanelCurves> panels = Arrays.asList(panelB);

        PanelOrderDetector detector = new PanelOrderDetector();
        boolean orderAtoF = detector.detectOrderAtoF(panels);

        assertTrue(orderAtoF, "Expected default A→F order when panel A not found");
    }

    private Curve2D createCurve(String id, double xPos) {
        List<Pt> points = Arrays.asList(
            new Pt(xPos, 50.0),
            new Pt(xPos, 75.0),
            new Pt(xPos, 100.0)
        );
        return new Curve2D(id, points);
    }

    private PanelCurves createPanel(PanelId panelId, Curve2D seamToPrev, Curve2D seamToNext) {
        Curve2D top = createCurve(panelId.name() + "_TOP", 100.0);
        Curve2D bottom = createCurve(panelId.name() + "_BOTTOM", 100.0);
        Curve2D waist = createCurve(panelId.name() + "_WAIST", 100.0);
        
        return new PanelCurves(
            panelId,
            top,
            bottom,
            waist,
            seamToPrev,
            createCurve(panelId.name() + "_PREV_DOWN", 50.0),
            seamToNext,
            createCurve(panelId.name() + "_NEXT_DOWN", 200.0)
        );
    }
}
