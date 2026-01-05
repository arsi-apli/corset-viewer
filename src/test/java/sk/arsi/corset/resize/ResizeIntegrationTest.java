package sk.arsi.corset.resize;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import sk.arsi.corset.io.SvgPanelLoader;
import sk.arsi.corset.model.PanelCurves;
import sk.arsi.corset.svg.SvgDocument;
import sk.arsi.corset.svg.SvgLoader;
import sk.arsi.corset.export.SvgExporter;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ResizeIntegrationTest {
    
    @Test
    void testResizeAndExport(@TempDir Path tempDir) throws Exception {
        // Load test pattern
        String testPatternPath = "patterns/Libra/v2/P2All-Final-V2-conic-no-image-26.64-clean.svg";
        File patternFile = new File(testPatternPath);
        
        if (!patternFile.exists()) {
            System.out.println("Test pattern not found, skipping test: " + testPatternPath);
            return;
        }
        
        SvgPanelLoader loader = new SvgPanelLoader(0.5, 2.0);
        List<PanelCurves> panels = loader.loadPanelsWithRetry(patternFile.toPath(), 1, 0);
        
        SvgLoader svgLoader = new SvgLoader();
        SvgDocument svgDoc = svgLoader.load(patternFile.toPath());
        
        assertNotNull(panels);
        assertTrue(panels.size() > 0, "Should have loaded panels");
        
        // Create resized panels with GLOBAL mode, 40mm resize
        double deltaMm = 40.0;
        ResizeMode mode = ResizeMode.GLOBAL;
        List<ResizedPanel> resizedPanels = new ArrayList<>();
        
        for (PanelCurves panel : panels) {
            ResizedPanel resized = PanelResizer.resizePanel(panel, mode, deltaMm, panels.size());
            assertNotNull(resized);
            resizedPanels.add(resized);
        }
        
        // Export resized SVG
        File outputFile = tempDir.resolve("test_resized.svg").toFile();
        SvgExporter.exportResizedSvg(svgDoc, panels, resizedPanels, mode, outputFile);
        
        // Verify output file was created
        assertTrue(outputFile.exists(), "Output file should exist");
        assertTrue(outputFile.length() > 0, "Output file should not be empty");
        
        // Verify it's valid XML/SVG
        String content = Files.readString(outputFile.toPath());
        assertTrue(content.contains("<?xml"), "Should be valid XML");
        assertTrue(content.contains("<svg"), "Should contain SVG root element");
        assertTrue(content.contains("_TOP"), "Should contain panel TOP curves");
        
        System.out.println("Resize export test passed!");
        System.out.println("Output file: " + outputFile.getAbsolutePath());
        System.out.println("File size: " + outputFile.length() + " bytes");
    }
    
    @Test
    void testResizeModes(@TempDir Path tempDir) throws Exception {
        // Load test pattern
        String testPatternPath = "patterns/Libra/v2/P2All-Final-V2-conic-no-image-26.64-clean.svg";
        File patternFile = new File(testPatternPath);
        
        if (!patternFile.exists()) {
            System.out.println("Test pattern not found, skipping test: " + testPatternPath);
            return;
        }
        
        SvgPanelLoader loader = new SvgPanelLoader(0.5, 2.0);
        List<PanelCurves> panels = loader.loadPanelsWithRetry(patternFile.toPath(), 1, 0);
        
        SvgLoader svgLoader = new SvgLoader();
        SvgDocument svgDoc = svgLoader.load(patternFile.toPath());
        
        double deltaMm = 20.0;
        
        // Test each resize mode
        for (ResizeMode mode : ResizeMode.values()) {
            List<ResizedPanel> resizedPanels = new ArrayList<>();
            
            for (PanelCurves panel : panels) {
                ResizedPanel resized = PanelResizer.resizePanel(panel, mode, deltaMm, panels.size());
                assertNotNull(resized, "Resized panel should not be null for mode: " + mode);
                assertEquals(mode, resized.getMode(), "Mode should match");
                resizedPanels.add(resized);
            }
            
            // Export for this mode
            File outputFile = tempDir.resolve("test_resized_" + mode.name().toLowerCase() + ".svg").toFile();
            SvgExporter.exportResizedSvg(svgDoc, panels, resizedPanels, mode, outputFile);
            
            assertTrue(outputFile.exists(), "Output file should exist for mode: " + mode);
            assertTrue(outputFile.length() > 0, "Output file should not be empty for mode: " + mode);
            
            System.out.println("Mode " + mode + " export test passed!");
        }
    }
}
