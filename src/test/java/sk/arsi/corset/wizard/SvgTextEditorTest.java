package sk.arsi.corset.wizard;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import sk.arsi.corset.model.Pt;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SvgTextEditorTest {

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
    void testSaveWithAssignments() throws Exception {
        String svgContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <svg xmlns="http://www.w3.org/2000/svg">
                <path id="path1" d="M 0 0 L 10 10" />
                <path id="path2" d="M 20 20 L 30 30" />
                <path d="M 40 40 L 50 50" />
            </svg>
            """;
        
        // Create temp files
        Path sourceFile = Files.createTempFile("test_source", ".svg");
        Path targetFile = Files.createTempFile("test_target", ".svg");
        
        try {
            // Write source content
            Files.writeString(sourceFile, svgContent, StandardCharsets.UTF_8);
            
            // Create session
            Document doc = createTestDocument(svgContent);
            IdWizardSession session = new IdWizardSession(doc);
            
            // Assign first missing step to first non-green candidate
            SvgPathCandidate candidate = null;
            for (SvgPathCandidate c : session.getCandidates()) {
                if (!c.isGreen()) {
                    candidate = c;
                    break;
                }
            }
            assertNotNull(candidate);
            session.assignCurrent(candidate);
            
            // Save
            SvgTextEditor editor = new SvgTextEditor();
            editor.saveWithAssignments(sourceFile, targetFile, session);
            
            // Verify target file was created and contains the new ID
            assertTrue(Files.exists(targetFile));
            String result = Files.readString(targetFile, StandardCharsets.UTF_8);
            
            // Should contain the assigned ID
            RequiredPath firstStep = RequiredPath.steps().get(0);
            assertTrue(result.contains("id=\"" + firstStep.svgId() + "\""), 
                "Result should contain assigned ID: " + firstStep.svgId());
            
        } finally {
            Files.deleteIfExists(sourceFile);
            Files.deleteIfExists(targetFile);
        }
    }

    @Test
    void testUpdatePathWithExistingId() throws Exception {
        String svgContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <svg xmlns="http://www.w3.org/2000/svg">
                <path id="old_id" d="M 0 0 L 10 10" />
            </svg>
            """;
        
        Path sourceFile = Files.createTempFile("test_update", ".svg");
        Path targetFile = Files.createTempFile("test_update_out", ".svg");
        
        try {
            Files.writeString(sourceFile, svgContent, StandardCharsets.UTF_8);
            
            Document doc = createTestDocument(svgContent);
            IdWizardSession session = new IdWizardSession(doc);
            
            // Find the candidate with old_id
            SvgPathCandidate candidate = null;
            for (SvgPathCandidate c : session.getCandidates()) {
                if ("old_id".equals(c.getOriginalId())) {
                    candidate = c;
                    break;
                }
            }
            assertNotNull(candidate);
            
            // Assign it
            session.assignCurrent(candidate);
            
            // Save
            SvgTextEditor editor = new SvgTextEditor();
            editor.saveWithAssignments(sourceFile, targetFile, session);
            
            String result = Files.readString(targetFile, StandardCharsets.UTF_8);
            
            // Should have replaced old_id with the new required ID
            assertFalse(result.contains("id=\"old_id\""), "old_id should be replaced");
            assertTrue(result.contains("id=\"" + session.getAllSteps().get(0).svgId() + "\""), 
                "Should contain new required ID");
            
        } finally {
            Files.deleteIfExists(sourceFile);
            Files.deleteIfExists(targetFile);
        }
    }

    @Test
    void testInsertIdIntoPathWithoutId() throws Exception {
        String svgContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <svg xmlns="http://www.w3.org/2000/svg">
                <path d="M 0 0 L 10 10" />
            </svg>
            """;
        
        Path sourceFile = Files.createTempFile("test_insert", ".svg");
        Path targetFile = Files.createTempFile("test_insert_out", ".svg");
        
        try {
            Files.writeString(sourceFile, svgContent, StandardCharsets.UTF_8);
            
            Document doc = createTestDocument(svgContent);
            IdWizardSession session = new IdWizardSession(doc);
            
            // Assign first candidate
            SvgPathCandidate candidate = session.getCandidates().get(0);
            session.assignCurrent(candidate);
            
            // Save
            SvgTextEditor editor = new SvgTextEditor();
            editor.saveWithAssignments(sourceFile, targetFile, session);
            
            String result = Files.readString(targetFile, StandardCharsets.UTF_8);
            
            // Should have inserted the ID
            assertTrue(result.contains("id=\"" + session.getAllSteps().get(0).svgId() + "\""), 
                "Should contain new required ID");
            
        } finally {
            Files.deleteIfExists(sourceFile);
            Files.deleteIfExists(targetFile);
        }
    }
}
