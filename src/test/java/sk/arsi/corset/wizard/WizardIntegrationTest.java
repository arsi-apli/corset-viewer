package sk.arsi.corset.wizard;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test demonstrating the complete wizard flow.
 */
class WizardIntegrationTest {

    private Document createTestDocument(String svgContent) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new ByteArrayInputStream(svgContent.getBytes(StandardCharsets.UTF_8)));
    }

    @BeforeEach
    void setUp() {
        RequiredPath.resetAllAssignments();
    }

    @Test
    void testCompleteWizardFlow() throws Exception {
        // Simulates a user going through the wizard to assign all missing IDs
        
        // 1. Create an SVG with some required IDs present and many missing
        String originalSvg = """
            <?xml version="1.0" encoding="UTF-8"?>
            <svg xmlns="http://www.w3.org/2000/svg">
                <!-- Already has A_TOP -->
                <path id="A_TOP" d="M 0 0 L 100 0" />
                <!-- Missing: A_WAIST - this will be path1 -->
                <path id="path1" d="M 0 10 L 100 10" />
                <!-- Missing: A_BOTTOM - this will be path2 -->
                <path id="path2" d="M 0 20 L 100 20" />
                <!-- Add a few more paths for other required IDs -->
                <path id="path3" d="M 0 30 L 100 30" />
                <path id="path4" d="M 0 40 L 100 40" />
            </svg>
            """;
        
        Path sourceFile = Files.createTempFile("wizard_test", ".svg");
        Path targetFile = sourceFile.getParent().resolve("wizard_test_corset_viewer.svg");
        
        try {
            Files.writeString(sourceFile, originalSvg, StandardCharsets.UTF_8);
            
            // 2. Load SVG and create wizard session
            Document doc = createTestDocument(originalSvg);
            IdWizardSession session = new IdWizardSession(doc);
            
            // 3. Verify A_TOP is already assigned
            assertTrue(RequiredPath.A_TOP.isAssigned(), "A_TOP should be pre-assigned");
            
            // 4. Verify we have 41 missing steps (42 - 1 that's already assigned)
            assertEquals(41, session.totalMissing(), "Should have 41 missing IDs");
            
            // 5. Get all unassigned candidates (should be 4: path1, path2, path3, path4)
            long unassignedCount = session.getCandidates().stream()
                .filter(c -> !c.isGreen())
                .count();
            assertEquals(4, unassignedCount, "Should have 4 unassigned candidates");
            
            // 6. Simulate user assigning the first 4 missing steps
            for (int i = 0; i < 4; i++) {
                // Get current step
                RequiredPath currentStep = session.currentStep();
                assertNotNull(currentStep, "Should have a current step");
                
                // Find first unassigned candidate
                SvgPathCandidate candidate = null;
                for (SvgPathCandidate c : session.getCandidates()) {
                    if (!c.isGreen()) {
                        candidate = c;
                        break;
                    }
                }
                assertNotNull(candidate, "Should have an unassigned candidate");
                
                // Assign it
                session.assignCurrent(candidate);
                
                // Verify assignment
                assertTrue(currentStep.isAssigned(), "Step should be assigned");
                assertEquals(currentStep, candidate.getAssignedRequired(), 
                    "Candidate should reference the step");
                assertTrue(candidate.isGreen(), "Candidate should now be green");
            }
            
            // 7. We've assigned 4 more IDs, so now we have 37 remaining
            assertEquals(37, session.remaining(), "Should have 37 remaining steps");
            
            // 8. Save the modified SVG
            SvgTextEditor editor = new SvgTextEditor();
            editor.saveWithAssignments(sourceFile, targetFile, session);
            
            // 9. Verify the saved file contains the new assignments
            assertTrue(Files.exists(targetFile), "Target file should exist");
            String savedContent = Files.readString(targetFile, StandardCharsets.UTF_8);
            
            // Should still have A_TOP
            assertTrue(savedContent.contains("id=\"A_TOP\""), "Should contain A_TOP");
            
            // Should have the assigned IDs (first 4 missing steps)
            // A_TOP was already assigned, so missing starts with A_WAIST
            assertTrue(savedContent.contains("id=\"A_WAIST\""), "Should contain A_WAIST");
            assertTrue(savedContent.contains("id=\"A_BOTTOM\""), "Should contain A_BOTTOM");
            assertTrue(savedContent.contains("id=\"AA_UP\""), "Should contain AA_UP");
            assertTrue(savedContent.contains("id=\"AA_DOWN\""), "Should contain AA_DOWN");
            
            // Original path ids should be replaced/removed
            assertFalse(savedContent.contains("id=\"path1\""), "path1 should be replaced");
            assertFalse(savedContent.contains("id=\"path2\""), "path2 should be replaced");
            
            // 10. Verify we can reload the session from the saved file and continue
            Document savedDoc = createTestDocument(savedContent);
            IdWizardSession newSession = new IdWizardSession(savedDoc);
            
            // Should have 37 missing steps now (started with 41, assigned 4)
            assertEquals(37, newSession.totalMissing(), 
                "New session should have 37 missing steps");
            
            // A_TOP, A_WAIST, A_BOTTOM, AA_UP, AA_DOWN should all be pre-assigned
            assertTrue(RequiredPath.A_TOP.isAssigned());
            assertTrue(RequiredPath.A_WAIST.isAssigned());
            assertTrue(RequiredPath.A_BOTTOM.isAssigned());
            assertTrue(RequiredPath.AA_UP.isAssigned());
            assertTrue(RequiredPath.AA_DOWN.isAssigned());
            
        } finally {
            Files.deleteIfExists(sourceFile);
            Files.deleteIfExists(targetFile);
        }
    }

    @Test
    void testWizardWithAllIdsPresent() throws Exception {
        // Create SVG with all required IDs present
        StringBuilder svgBuilder = new StringBuilder("""
            <?xml version="1.0" encoding="UTF-8"?>
            <svg xmlns="http://www.w3.org/2000/svg">
            """);
        
        for (RequiredPath path : RequiredPath.values()) {
            svgBuilder.append(String.format(
                "    <path id=\"%s\" d=\"M 0 0 L 10 10\" />\n", 
                path.svgId()
            ));
        }
        svgBuilder.append("</svg>");
        
        Document doc = createTestDocument(svgBuilder.toString());
        IdWizardSession session = new IdWizardSession(doc);
        
        // Should have no missing steps
        assertEquals(0, session.totalMissing(), "Should have no missing steps");
        assertTrue(session.isComplete(), "Session should be complete");
        assertNull(session.currentStep(), "Should have no current step");
        
        // All candidates should be green
        for (SvgPathCandidate candidate : session.getCandidates()) {
            assertTrue(candidate.isGreen(), "All candidates should be green");
        }
    }
}
