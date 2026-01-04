package sk.arsi.corset.export;

import org.junit.jupiter.api.Test;
import sk.arsi.corset.model.PanelCurves;
import sk.arsi.corset.svg.PathSampler;
import sk.arsi.corset.svg.PatternContract;
import sk.arsi.corset.svg.PatternExtractor;
import sk.arsi.corset.svg.SvgDocument;
import sk.arsi.corset.svg.SvgLoader;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test that verifies export works with actual SVG patterns.
 * This test uses sample patterns from the repository.
 */
public class RealPatternExportTest {

    @Test
    public void testExportWithRealPattern() throws Exception {
        // Path to sample pattern (relative to project root)
        String patternPath = "patterns/Libra/v2/P2All-Final-V2-conic-no-image-26.64-clean.svg";
        File patternFile = new File(patternPath);
        
        if (!patternFile.exists()) {
            System.out.println("Sample pattern not found at: " + patternFile.getAbsolutePath());
            System.out.println("Skipping real pattern export test.");
            return; // Skip test if pattern file doesn't exist
        }
        
        System.out.println("Loading pattern from: " + patternFile.getAbsolutePath());
        
        // Load the SVG document
        SvgLoader loader = new SvgLoader();
        SvgDocument doc = loader.load(patternFile.toPath());
        assertNotNull(doc, "SVG document should be loaded");
        
        // Extract panels
        PatternContract contract = new PatternContract();
        PathSampler sampler = new PathSampler();
        PatternExtractor extractor = new PatternExtractor(contract, sampler);
        List<PanelCurves> panels = extractor.extractPanels(doc, 0.2, 0.5);
        
        assertNotNull(panels, "Panels should be extracted");
        assertFalse(panels.isEmpty(), "Should have at least one panel");
        System.out.println("Loaded " + panels.size() + " panels");
        
        // Export with notches
        File outputFile = new File("target/test-output-with-notches.svg");
        outputFile.getParentFile().mkdirs();
        
        int notchCount = 3;
        double notchLength = 4.0;
        double allowanceDistance = 10.0;
        
        System.out.println("Exporting to: " + outputFile.getAbsolutePath());
        System.out.println("Notch count: " + notchCount);
        System.out.println("Notch length: " + notchLength + " mm");
        System.out.println("Allowance distance: " + allowanceDistance + " mm");
        
        SvgExporter.exportWithAllowancesAndNotches(doc, panels, outputFile, notchCount, notchLength, allowanceDistance);
        
        // Verify output file was created
        assertTrue(outputFile.exists(), "Output SVG file should be created");
        assertTrue(outputFile.length() > 0, "Output SVG file should not be empty");
        
        System.out.println("Export successful!");
        System.out.println("Output file size: " + outputFile.length() + " bytes");
        System.out.println("You can open the file in Inkscape to verify: " + outputFile.getAbsolutePath());
    }
}
