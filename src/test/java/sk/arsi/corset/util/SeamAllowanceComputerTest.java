package sk.arsi.corset.util;

import org.junit.jupiter.api.Test;
import sk.arsi.corset.model.Curve2D;
import sk.arsi.corset.model.PanelCurves;
import sk.arsi.corset.model.PanelId;
import sk.arsi.corset.model.Pt;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SeamAllowanceComputer.
 */
public class SeamAllowanceComputerTest {

    @Test
    public void testShouldGenerateAllowance_InternalSeams() {
        // Internal seams should generate allowances
        assertTrue(SeamAllowanceComputer.shouldGenerateAllowance("AB_UP"));
        assertTrue(SeamAllowanceComputer.shouldGenerateAllowance("AB_DOWN"));
        assertTrue(SeamAllowanceComputer.shouldGenerateAllowance("BA_UP"));
        assertTrue(SeamAllowanceComputer.shouldGenerateAllowance("BA_DOWN"));
        
        assertTrue(SeamAllowanceComputer.shouldGenerateAllowance("BC_UP"));
        assertTrue(SeamAllowanceComputer.shouldGenerateAllowance("CD_DOWN"));
        assertTrue(SeamAllowanceComputer.shouldGenerateAllowance("DE_UP"));
        assertTrue(SeamAllowanceComputer.shouldGenerateAllowance("EF_DOWN"));
        
        // Reverse directions
        assertTrue(SeamAllowanceComputer.shouldGenerateAllowance("CB_UP"));
        assertTrue(SeamAllowanceComputer.shouldGenerateAllowance("DC_DOWN"));
        assertTrue(SeamAllowanceComputer.shouldGenerateAllowance("ED_UP"));
        assertTrue(SeamAllowanceComputer.shouldGenerateAllowance("FE_DOWN"));
    }

    @Test
    public void testShouldGenerateAllowance_OuterSeams() {
        // Outer seams (AA and FF) should NOT generate allowances
        assertFalse(SeamAllowanceComputer.shouldGenerateAllowance("AA_UP"));
        assertFalse(SeamAllowanceComputer.shouldGenerateAllowance("AA_DOWN"));
        assertFalse(SeamAllowanceComputer.shouldGenerateAllowance("FF_UP"));
        assertFalse(SeamAllowanceComputer.shouldGenerateAllowance("FF_DOWN"));
    }

    @Test
    public void testShouldGenerateAllowance_InvalidIds() {
        assertFalse(SeamAllowanceComputer.shouldGenerateAllowance(null));
        assertFalse(SeamAllowanceComputer.shouldGenerateAllowance(""));
        assertFalse(SeamAllowanceComputer.shouldGenerateAllowance("INVALID"));
        assertFalse(SeamAllowanceComputer.shouldGenerateAllowance("A_TOP"));
    }

    @Test
    public void testComputeOffsetCurve_SimpleVerticalLine() {
        // Create a simple panel with a vertical seam
        // Panel interior (waist centroid) at (50, 100)
        Curve2D waist = new Curve2D("A_WAIST", Arrays.asList(
                new Pt(40, 100),
                new Pt(60, 100)
        ));

        // Vertical seam at x=70 (to the right of waist centroid)
        Curve2D seam = new Curve2D("AB_UP", Arrays.asList(
                new Pt(70, 80),
                new Pt(70, 90),
                new Pt(70, 100)
        ));

        PanelCurves panel = new PanelCurves(
                PanelId.A,
                null, // top
                null, // bottom
                waist,
                null, null,
                seam, // seamToNextUp
                null
        );

        // Compute offset with 10mm allowance
        List<Pt> offsetPoints = SeamAllowanceComputer.computeOffsetCurve(seam, panel, 10.0);

        assertNotNull(offsetPoints);
        assertEquals(3, offsetPoints.size());

        // For a vertical line to the right of the interior,
        // the offset should move further to the right (away from interior)
        // So all offset points should have x > 70
        for (Pt p : offsetPoints) {
            assertTrue(p.getX() > 70, "Offset point should be to the right of original: " + p.getX());
        }
    }

    @Test
    public void testComputeOffsetCurve_NullInputs() {
        Curve2D waist = new Curve2D("A_WAIST", Arrays.asList(
                new Pt(40, 100),
                new Pt(60, 100)
        ));

        Curve2D seam = new Curve2D("AB_UP", Arrays.asList(
                new Pt(70, 80),
                new Pt(70, 100)
        ));

        PanelCurves panel = new PanelCurves(
                PanelId.A, null, null, waist, null, null, seam, null
        );

        // Null curve
        assertNull(SeamAllowanceComputer.computeOffsetCurve(null, panel, 10.0));

        // Null panel
        assertNull(SeamAllowanceComputer.computeOffsetCurve(seam, null, 10.0));

        // Zero or negative distance
        assertNull(SeamAllowanceComputer.computeOffsetCurve(seam, panel, 0.0));
        assertNull(SeamAllowanceComputer.computeOffsetCurve(seam, panel, -5.0));
    }
}
