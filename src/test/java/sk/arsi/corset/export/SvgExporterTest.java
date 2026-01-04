package sk.arsi.corset.export;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import sk.arsi.corset.io.SvgPanelLoader;
import sk.arsi.corset.model.PanelCurves;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for SvgExporter.
 */
public class SvgExporterTest {

    @TempDir
    Path tempDir;

    @Test
    public void testExportWithAllowances() throws Exception {
        // Load sample panels from test SVG
        Path testSvg = Path.of("patterns/Libra/v2/P2All-Final-V2-conic-no-image-26.64-clean.svg");
        if (!Files.exists(testSvg)) {
            // Skip test if sample file not available
            return;
        }

        SvgPanelLoader loader = new SvgPanelLoader(0.2, 0.5);
        List<PanelCurves> panels = loader.loadPanelsWithRetry(testSvg, 3, 250);

        assertNotNull(panels);
        assertFalse(panels.isEmpty());

        // Export with 10mm allowance
        File outputFile = tempDir.resolve("test_export_with_allowances.svg").toFile();
        SvgExporter.exportWithAllowances(panels, 10.0, outputFile);

        // Verify file was created
        assertTrue(outputFile.exists());
        assertTrue(outputFile.length() > 0);

        // Read file content and verify structure
        String content = Files.readString(outputFile.toPath());
        
        // Check for SVG structure
        assertTrue(content.contains("<svg"));
        assertTrue(content.contains("xmlns=\"http://www.w3.org/2000/svg\""));
        
        // Check for layers
        assertTrue(content.contains("id=\"panels\""));
        assertTrue(content.contains("id=\"allowances\""));
        
        // Check for at least one allowance path
        // Internal seams should have allowances (e.g., AB_UP_ALLOW, BC_DOWN_ALLOW, etc.)
        boolean hasAllowancePath = content.contains("_ALLOW");
        assertTrue(hasAllowancePath, "Should have at least one allowance path");
        
        // Verify AA and FF seams do NOT have allowances
        assertFalse(content.contains("AA_UP_ALLOW"), "AA_UP should not have allowance");
        assertFalse(content.contains("AA_DOWN_ALLOW"), "AA_DOWN should not have allowance");
        assertFalse(content.contains("FF_UP_ALLOW"), "FF_UP should not have allowance");
        assertFalse(content.contains("FF_DOWN_ALLOW"), "FF_DOWN should not have allowance");
    }

    @Test
    public void testExportWithAllowances_EmptyPanels() {
        File outputFile = tempDir.resolve("test_empty.svg").toFile();
        
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            SvgExporter.exportWithAllowances(null, 10.0, outputFile);
        });
        
        assertTrue(exception.getMessage().contains("No panels"));
    }
}
