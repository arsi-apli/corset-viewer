package sk.arsi.corset.allowance;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import sk.arsi.corset.io.SvgPanelLoader;
import sk.arsi.corset.model.PanelCurves;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for SVG allowance export.
 */
public class SvgAllowanceExportTest {

    @Test
    public void testExportWithAllowances(@TempDir Path tempDir) throws Exception {
        // Find the test SVG file
        Path testSvg = Paths.get("patterns/Libra/v2/P2All-Final-V2-conic-no-image-26.64-clean.svg");
        if (!Files.exists(testSvg)) {
            // Skip test if file not available
            System.out.println("Test SVG not found, skipping test: " + testSvg);
            return;
        }

        // Copy to temp directory
        Path inputSvg = tempDir.resolve("test.svg");
        Files.copy(testSvg, inputSvg, StandardCopyOption.REPLACE_EXISTING);

        // Load panels
        SvgPanelLoader loader = new SvgPanelLoader(0.2, 0.5);
        List<PanelCurves> panels = loader.loadPanelsWithRetry(inputSvg, 1, 0);
        assertNotNull(panels);
        assertFalse(panels.isEmpty(), "Should have loaded some panels");

        // Export with allowances
        OutputFileNamer namer = new OutputFileNamer();
        Path outputPath = namer.generateOutputPath(inputSvg);
        
        SvgAllowanceExporter exporter = new SvgAllowanceExporter();
        exporter.export(inputSvg, outputPath, panels, 10.0);

        // Verify output file was created
        assertTrue(Files.exists(outputPath), "Output file should exist");
        assertTrue(Files.size(outputPath) > 0, "Output file should not be empty");

        // Verify file name
        assertEquals("test_corset_viewer_allowances.svg", outputPath.getFileName().toString());

        // Verify the SVG contains the allowances group
        String content = Files.readString(outputPath);
        assertTrue(content.contains("id=\"allowances\""), "Should contain allowances group");
        assertTrue(content.contains("_ALLOW\""), "Should contain allowance path IDs");
    }
}
