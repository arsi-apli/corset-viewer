package sk.arsi.corset.wizard;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import sk.arsi.corset.model.Pt;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class IdWizardSessionTest {

    private Document createTestDocument(String svgContent) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new ByteArrayInputStream(svgContent.getBytes(StandardCharsets.UTF_8)));
    }

    @BeforeEach
    void setUp() {
        // Reset all assignments before each test
        RequiredPath.resetAllAssignments();
    }

    @Test
    void testSessionWithNoRequiredIds() throws Exception {
        String svg = """
            <svg xmlns="http://www.w3.org/2000/svg">
                <path id="path1" d="M 0 0 L 10 10" />
                <path id="path2" d="M 20 20 L 30 30" />
            </svg>
            """;
        
        Document doc = createTestDocument(svg);
        IdWizardSession session = new IdWizardSession(doc);
        
        // All steps should be missing
        assertEquals(42, session.totalMissing(), "All 42 required IDs should be missing");
        assertEquals(42, session.remaining());
        assertFalse(session.isComplete());
        assertNotNull(session.currentStep());
    }

    @Test
    void testSessionWithSomeRequiredIds() throws Exception {
        String svg = """
            <svg xmlns="http://www.w3.org/2000/svg">
                <path id="A_TOP" d="M 0 0 L 10 10" />
                <path id="A_WAIST" d="M 20 20 L 30 30" />
                <path id="path3" d="M 40 40 L 50 50" />
            </svg>
            """;
        
        Document doc = createTestDocument(svg);
        IdWizardSession session = new IdWizardSession(doc);
        
        // 2 required IDs are present, so 40 should be missing
        assertEquals(40, session.totalMissing(), "40 required IDs should be missing");
        assertEquals(40, session.remaining());
        
        // Verify that A_TOP and A_WAIST are pre-assigned
        assertTrue(RequiredPath.A_TOP.isAssigned());
        assertTrue(RequiredPath.A_WAIST.isAssigned());
        assertFalse(RequiredPath.A_BOTTOM.isAssigned());
    }

    @Test
    void testSessionAdvancesAfterAssignment() throws Exception {
        String svg = """
            <svg xmlns="http://www.w3.org/2000/svg">
                <path id="path1" d="M 0 0 L 10 10" />
                <path id="path2" d="M 20 20 L 30 30" />
            </svg>
            """;
        
        Document doc = createTestDocument(svg);
        IdWizardSession session = new IdWizardSession(doc);
        
        RequiredPath firstStep = session.currentStep();
        assertNotNull(firstStep);
        assertEquals(1, session.currentStepNumber());
        
        // Assign first step to first candidate
        SvgPathCandidate candidate = session.getCandidates().get(0);
        session.assignCurrent(candidate);
        
        // Should advance to next step
        assertEquals(2, session.currentStepNumber());
        assertEquals(41, session.remaining());
        assertNotEquals(firstStep, session.currentStep());
        
        // Verify assignment
        assertTrue(firstStep.isAssigned());
        assertEquals(candidate, firstStep.getAssignedCandidate());
        assertEquals(firstStep, candidate.getAssignedRequired());
    }

    @Test
    void testSessionCompletes() throws Exception {
        // Create a document with all but one required ID
        StringBuilder svgBuilder = new StringBuilder("<svg xmlns=\"http://www.w3.org/2000/svg\">\n");
        List<RequiredPath> allSteps = RequiredPath.steps();
        
        // Add all required paths except the last one
        for (int i = 0; i < allSteps.size() - 1; i++) {
            RequiredPath path = allSteps.get(i);
            svgBuilder.append(String.format("  <path id=\"%s\" d=\"M %d 0 L %d 10\" />\n", 
                path.svgId(), i * 10, i * 10 + 5));
        }
        
        // Add one unassigned path for the last step
        svgBuilder.append("  <path id=\"unassigned\" d=\"M 1000 1000 L 1010 1010\" />\n");
        svgBuilder.append("</svg>");
        
        Document doc = createTestDocument(svgBuilder.toString());
        IdWizardSession session = new IdWizardSession(doc);
        
        // Only one step should be missing
        assertEquals(1, session.totalMissing());
        assertFalse(session.isComplete());
        
        // Find the unassigned candidate
        SvgPathCandidate unassigned = null;
        for (SvgPathCandidate candidate : session.getCandidates()) {
            if (!candidate.isGreen()) {
                unassigned = candidate;
                break;
            }
        }
        assertNotNull(unassigned);
        
        // Assign it
        session.assignCurrent(unassigned);
        
        // Session should be complete
        assertTrue(session.isComplete());
        assertEquals(0, session.remaining());
        assertNull(session.currentStep());
    }

    @Test
    void testCannotAssignGreenCandidate() throws Exception {
        String svg = """
            <svg xmlns="http://www.w3.org/2000/svg">
                <path id="A_TOP" d="M 0 0 L 10 10" />
                <path id="path2" d="M 20 20 L 30 30" />
            </svg>
            """;
        
        Document doc = createTestDocument(svg);
        IdWizardSession session = new IdWizardSession(doc);
        
        // Find the A_TOP candidate (should be green)
        SvgPathCandidate greenCandidate = null;
        for (SvgPathCandidate candidate : session.getCandidates()) {
            if (candidate.isGreen()) {
                greenCandidate = candidate;
                break;
            }
        }
        assertNotNull(greenCandidate);
        
        // Trying to assign a green candidate should throw
        final SvgPathCandidate finalGreenCandidate = greenCandidate;
        assertThrows(IllegalArgumentException.class, () -> {
            session.assignCurrent(finalGreenCandidate);
        });
    }

    @Test
    void testCandidatesAreBuilt() throws Exception {
        String svg = """
            <svg xmlns="http://www.w3.org/2000/svg">
                <path id="path1" d="M 0 0 L 10 10" />
                <path id="A_TOP" d="M 20 20 L 30 30" />
                <path id="path3" d="M 40 40 L 50 50" />
            </svg>
            """;
        
        Document doc = createTestDocument(svg);
        IdWizardSession session = new IdWizardSession(doc);
        
        List<SvgPathCandidate> candidates = session.getCandidates();
        assertEquals(3, candidates.size());
        
        // Check properties of candidates
        for (SvgPathCandidate candidate : candidates) {
            assertNotNull(candidate.getDAttribute());
            assertNotNull(candidate.getPolyline());
            assertTrue(candidate.getPolyline().size() >= 2);
        }
        
        // One candidate should be originally required
        long requiredCount = candidates.stream()
            .filter(SvgPathCandidate::isOriginallyRequiredId)
            .count();
        assertEquals(1, requiredCount);
    }
}
