package sk.arsi.corset.export;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import sk.arsi.corset.io.SvgPanelLoader;
import sk.arsi.corset.model.PanelCurves;
import sk.arsi.corset.svg.SvgDocument;
import sk.arsi.corset.svg.SvgLoader;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for SvgExporter.
 */
public class SvgExporterTest {

    private static final String TEST_SVG_PATH = "patterns/Libra/v2/P2All-Final-V2-conic-no-image-26.64-clean.svg";

    @TempDir
    Path tempDir;

    @Test
    public void testExportWithAllowances() throws Exception {
        // Load sample panels from test SVG
        Path testSvg = Path.of(TEST_SVG_PATH);
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

    @Test
    public void testExportWithAllowancesAndNotches() throws Exception {
        // Load sample panels from test SVG
        Path testSvg = Path.of(TEST_SVG_PATH);
        if (!Files.exists(testSvg)) {
            // Skip test if sample file not available
            return;
        }

        // Load SVG document
        SvgLoader svgLoader = new SvgLoader();
        SvgDocument svgDocument = svgLoader.load(testSvg);

        // Load panels
        SvgPanelLoader loader = new SvgPanelLoader(0.2, 0.5);
        List<PanelCurves> panels = loader.loadPanelsWithRetry(testSvg, 3, 250);

        assertNotNull(panels);
        assertFalse(panels.isEmpty());

        // Export with allowances and notches
        File outputFile = tempDir.resolve("test_export_with_allowances_and_notches.svg").toFile();
        SvgExporter.exportWithAllowancesAndNotches(svgDocument, panels, outputFile, 3, 4.0, 10.0);

        // Verify file was created
        assertTrue(outputFile.exists());
        assertTrue(outputFile.length() > 0);

        // Read file content and verify structure
        String content = Files.readString(outputFile.toPath());
        
        // Check for SVG structure
        assertTrue(content.contains("<svg"));
        assertTrue(content.contains("xmlns=\"http://www.w3.org/2000/svg\""));
        
        // Check that original elements are preserved (e.g., waist elements)
        assertTrue(content.contains("A_WAIST"), "Original A_WAIST element should be preserved");
        assertTrue(content.contains("B_WAIST"), "Original B_WAIST element should be preserved");
        assertTrue(content.contains("C_WAIST"), "Original C_WAIST element should be preserved");
        
        // Check for ALLOWANCES groups
        assertTrue(content.contains("A_ALLOWANCES"), "Should have A_ALLOWANCES group");
        assertTrue(content.contains("B_ALLOWANCES"), "Should have B_ALLOWANCES group");
        assertTrue(content.contains("C_ALLOWANCES"), "Should have C_ALLOWANCES group");
        
        // Check for NOTCHES groups
        assertTrue(content.contains("A_NOTCHES"), "Should have A_NOTCHES group");
        assertTrue(content.contains("B_NOTCHES"), "Should have B_NOTCHES group");
        assertTrue(content.contains("C_NOTCHES"), "Should have C_NOTCHES group");
        
        // Verify at least one allowance and one notch path exists
        boolean hasAllowancePath = content.contains("_ALLOW");
        assertTrue(hasAllowancePath, "Should have at least one allowance path");
        
        boolean hasNotchPath = content.contains("_NOTCH");
        assertTrue(hasNotchPath, "Should have at least one notch path");
        
        // Verify original stroke-width is preserved for waist lines
        // The original SVG has waist lines with stroke-width around 0.5
        assertTrue(content.contains("stroke-width"), "Should preserve stroke-width attributes");
    }

    @Test
    public void testExportWithAllowancesAndNotches_NullDocument() {
        File outputFile = tempDir.resolve("test_null_doc.svg").toFile();
        
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            SvgExporter.exportWithAllowancesAndNotches(null, null, outputFile, 3, 4.0, 10.0);
        });
        
        assertTrue(exception.getMessage().contains("SVG document"));
    }

    @Test
    public void testExportWithAllowancesAndNotches_EmptyPanels() throws Exception {
        // Load SVG document
        Path testSvg = Path.of(TEST_SVG_PATH);
        if (!Files.exists(testSvg)) {
            // Skip test if sample file not available
            return;
        }
        
        SvgLoader svgLoader = new SvgLoader();
        SvgDocument svgDocument = svgLoader.load(testSvg);
        
        File outputFile = tempDir.resolve("test_empty_panels.svg").toFile();
        
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            SvgExporter.exportWithAllowancesAndNotches(svgDocument, null, outputFile, 3, 4.0, 10.0);
        });
        
        assertTrue(exception.getMessage().contains("No panels"));
    }

    @Test
    public void testExportCurvesOnly() throws Exception {
        // Load sample panels from test SVG
        Path testSvg = Path.of(TEST_SVG_PATH);
        if (!Files.exists(testSvg)) {
            // Skip test if sample file not available
            return;
        }

        // Load SVG document
        SvgLoader svgLoader = new SvgLoader();
        SvgDocument svgDocument = svgLoader.load(testSvg);

        // Load panels
        SvgPanelLoader loader = new SvgPanelLoader(0.2, 0.5);
        List<PanelCurves> panels = loader.loadPanelsWithRetry(testSvg, 3, 250);

        assertNotNull(panels);
        assertFalse(panels.isEmpty());

        // Export curves only (without resize, all curves should remain unchanged)
        File outputFile = tempDir.resolve("test_export_curves_only.svg").toFile();
        SvgExporter.exportCurvesOnly(testSvg, svgDocument, panels, outputFile);

        // Verify file was created
        assertTrue(outputFile.exists());
        assertTrue(outputFile.length() > 0);

        // Read both original and output files
        String originalContent = Files.readString(testSvg);
        String outputContent = Files.readString(outputFile.toPath());
        
        // Since no curves were modified, output should be identical to original
        // (or very similar - may differ in whitespace normalization)
        assertTrue(outputContent.contains("<svg"));
        assertTrue(outputContent.contains("xmlns=\"http://www.w3.org/2000/svg\""));
        
        // Check that original metadata is preserved
        assertTrue(outputContent.contains("inkscape:"), "Should preserve Inkscape metadata");
        
        // Verify specific path IDs exist
        assertTrue(outputContent.contains("id=\"A_WAIST\""));
        assertTrue(outputContent.contains("id=\"B_WAIST\""));
        assertTrue(outputContent.contains("id=\"C_WAIST\""));
        
        // Verify no allowances or notches added
        assertFalse(outputContent.contains("_ALLOW"), "Should not contain allowance paths");
        assertFalse(outputContent.contains("_NOTCHES"), "Should not contain notches groups");
        assertFalse(outputContent.contains("_NOTCH"), "Should not contain notch paths");
    }

    @Test
    public void testExportCurvesOnly_NullSvgPath() throws Exception {
        Path testSvg = Path.of(TEST_SVG_PATH);
        if (!Files.exists(testSvg)) {
            return;
        }

        SvgLoader svgLoader = new SvgLoader();
        SvgDocument svgDocument = svgLoader.load(testSvg);

        SvgPanelLoader loader = new SvgPanelLoader(0.2, 0.5);
        List<PanelCurves> panels = loader.loadPanelsWithRetry(testSvg, 3, 250);

        File outputFile = tempDir.resolve("test_null_path.svg").toFile();
        
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            SvgExporter.exportCurvesOnly(null, svgDocument, panels, outputFile);
        });
        
        assertTrue(exception.getMessage().contains("svgPath"));
    }

    @Test
    public void testExportCurvesOnly_NullDocument() throws Exception {
        Path testSvg = Path.of(TEST_SVG_PATH);
        if (!Files.exists(testSvg)) {
            return;
        }

        SvgPanelLoader loader = new SvgPanelLoader(0.2, 0.5);
        List<PanelCurves> panels = loader.loadPanelsWithRetry(testSvg, 3, 250);

        File outputFile = tempDir.resolve("test_null_doc.svg").toFile();
        
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            SvgExporter.exportCurvesOnly(testSvg, null, panels, outputFile);
        });
        
        assertTrue(exception.getMessage().contains("svgDocument"));
    }
}
