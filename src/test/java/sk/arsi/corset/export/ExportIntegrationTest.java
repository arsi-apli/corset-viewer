package sk.arsi.corset.export;

import org.junit.jupiter.api.Test;
import sk.arsi.corset.model.Curve2D;
import sk.arsi.corset.model.PanelCurves;
import sk.arsi.corset.model.PanelId;
import sk.arsi.corset.model.Pt;
import sk.arsi.corset.svg.SvgDocument;
import sk.arsi.corset.svg.SvgLoader;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for export functionality.
 */
public class ExportIntegrationTest {

    @Test
    public void testNotchGeneration() {
        // Create a simple test panel
        List<Pt> waistPoints = Arrays.asList(
            new Pt(0, 100),
            new Pt(50, 100)
        );
        Curve2D waist = new Curve2D("A_WAIST", waistPoints);
        
        // Vertical seam from y=0 to y=100 (UP curve)
        List<Pt> upPoints = Arrays.asList(
            new Pt(50, 100),
            new Pt(50, 0)
        );
        Curve2D seamUp = new Curve2D("AB_UP", upPoints);
        
        // Vertical seam from y=100 to y=200 (DOWN curve)
        List<Pt> downPoints = Arrays.asList(
            new Pt(50, 100),
            new Pt(50, 200)
        );
        Curve2D seamDown = new Curve2D("AB_DOWN", downPoints);
        
        PanelCurves panel = new PanelCurves(
            PanelId.A,
            null, // top
            null, // bottom
            waist,
            null, // seamToPrevUp
            null, // seamToPrevDown
            seamUp, // seamToNextUp
            seamDown  // seamToNextDown
        );
        
        List<PanelCurves> panels = Arrays.asList(panel);
        
        // Generate notches
        int notchCount = 3;
        double notchLength = 4.0;
        List<PanelNotches> allNotches = NotchGenerator.generateAllNotches(panels, notchCount, notchLength);
        
        assertNotNull(allNotches);
        assertEquals(1, allNotches.size());
        
        PanelNotches panelNotches = allNotches.get(0);
        assertEquals(PanelId.A, panelNotches.getPanelId());
        
        List<Notch> notches = panelNotches.getNotches();
        // Should have 6 notches on the right seam (toNext): 3 on UP curve, 3 on DOWN curve
        // Left seam (toPrev) is null, so no notches there
        assertEquals(6, notches.size());
        
        // Verify notch properties
        for (Notch notch : notches) {
            assertNotNull(notch.getStart());
            assertNotNull(notch.getEnd());
            assertNotNull(notch.getId());
            
            // Verify notch ID includes segment (UP or DOWN)
            assertTrue(notch.getId().contains("_UP_") || notch.getId().contains("_DOWN_"),
                "Notch ID should include segment identifier (UP or DOWN)");
            
            // Verify notch is horizontal (perpendicular to vertical seam)
            // Since seam is vertical at x=50, notches should point left (inward)
            // Start should be at x=50, end should be at x < 50
            assertEquals(50.0, notch.getStart().getX(), 0.01);
            assertTrue(notch.getEnd().getX() < 50.0, 
                "Notch should point inward (left) from x=50");
            
            // Verify notch length
            double dx = notch.getEnd().getX() - notch.getStart().getX();
            double dy = notch.getEnd().getY() - notch.getStart().getY();
            double length = Math.sqrt(dx * dx + dy * dy);
            assertEquals(notchLength, length, 0.01);
        }
    }

    @Test
    public void testNotchPositions() {
        // Create a simple vertical seam
        List<Pt> waistPoints = Arrays.asList(
            new Pt(0, 50),
            new Pt(50, 50)
        );
        Curve2D waist = new Curve2D("A_WAIST", waistPoints);
        
        // Vertical seam from y=0 to y=100
        List<Pt> upPoints = Arrays.asList(
            new Pt(50, 50),
            new Pt(50, 0)
        );
        Curve2D seamUp = new Curve2D("AB_UP", upPoints);
        
        List<Pt> downPoints = Arrays.asList(
            new Pt(50, 50),
            new Pt(50, 100)
        );
        Curve2D seamDown = new Curve2D("AB_DOWN", downPoints);
        
        PanelCurves panel = new PanelCurves(
            PanelId.A, null, null, waist, null, null, seamUp, seamDown
        );
        
        List<PanelCurves> panels = Arrays.asList(panel);
        
        // Generate 3 notches per curve segment
        List<PanelNotches> allNotches = NotchGenerator.generateAllNotches(panels, 3, 4.0);
        
        List<Notch> notches = allNotches.get(0).getNotches();
        // 6 notches total: 3 on UP curve + 3 on DOWN curve
        assertEquals(6, notches.size());
        
        // UP curve goes from y=50 to y=0, length=50
        // Notch positions should be at 25%, 50%, 75% along the UP curve = y=37.5, y=25, y=12.5
        // DOWN curve goes from y=50 to y=100, length=50
        // Notch positions should be at 25%, 50%, 75% along the DOWN curve = y=62.5, y=75, y=87.5
        List<Double> expectedYPositions = Arrays.asList(12.5, 25.0, 37.5, 62.5, 75.0, 87.5);
        List<Double> actualYPositions = new ArrayList<>();
        
        for (Notch notch : notches) {
            actualYPositions.add(notch.getStart().getY());
        }
        
        // Sort both lists for comparison
        actualYPositions.sort(Double::compareTo);
        expectedYPositions.sort(Double::compareTo);
        
        assertEquals(6, actualYPositions.size());
        for (int i = 0; i < 6; i++) {
            assertEquals(expectedYPositions.get(i), actualYPositions.get(i), 1.0,
                "Notch position " + i + " should be at expected Y coordinate");
        }
    }
}
