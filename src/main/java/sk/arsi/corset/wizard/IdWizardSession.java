package sk.arsi.corset.wizard;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import sk.arsi.corset.model.Pt;
import sk.arsi.corset.svg.PathSampler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Manages the wizard session state and two-way assignment between RequiredPath
 * and SvgPathCandidate.
 *
 * Updated version: - RequiredPath is no longer an enum; steps are generated
 * dynamically by RequiredPathSteps. - Supports variable panel range via a
 * maxPanel parameter (default 'F').
 */
public final class IdWizardSession {

    private final List<SvgPathCandidate> candidates;
    private final List<RequiredPath> allSteps;
    private final List<RequiredPath> missingSteps;
    private int currentStepIndex;

    /**
     * Backward-compatible constructor: assumes 6 panels (A..F).
     */
    public IdWizardSession(Document document) {
        this(document, 'F');
    }

    /**
     * @param document SVG DOM document
     * @param maxPanel max panel letter inclusive (e.g. 'F','G','H')
     */
    public IdWizardSession(Document document, char maxPanel) {
        this.allSteps = RequiredPathSteps.steps(maxPanel);
        this.candidates = buildCandidates(document, allSteps);

        // Pre-assign candidates that already have required IDs
        preAssignExistingIds();

        this.missingSteps = computeMissingSteps();
        this.currentStepIndex = 0;
    }

    /**
     * Build the list of path candidates from the SVG document.
     */
    private List<SvgPathCandidate> buildCandidates(Document document, List<RequiredPath> steps) {
        List<SvgPathCandidate> result = new ArrayList<>();

        Set<String> requiredIds = new HashSet<>();
        for (RequiredPath path : steps) {
            requiredIds.add(path.svgId());
        }

        PathSampler sampler = new PathSampler();
        int index = 0;

        // Find all path elements
        NodeList pathElements = document.getElementsByTagName("path");
        for (int i = 0; i < pathElements.getLength(); i++) {
            Node node = pathElements.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            Element element = (Element) node;
            String id = element.getAttribute("id");
            String d = element.getAttribute("d");

            if (d == null || d.trim().isEmpty()) {
                continue; // Skip paths without d attribute
            }

            boolean isRequired = (id != null) && requiredIds.contains(id);

            // Parse path to polyline for hit-testing
            List<Pt> polyline;
            try {
                String pathId = getPathIdOrDefault(id, index);
                polyline = sampler.samplePath(pathId, d, 0.5).getPoints();
            } catch (Exception e) {
                // Skip invalid paths
                continue;
            }

            SvgPathCandidate candidate = new SvgPathCandidate(
                    index++,
                    id,
                    d,
                    isRequired,
                    polyline
            );

            result.add(candidate);
        }

        return result;
    }

    /**
     * Pre-assign candidates that already have required IDs. This replaces the
     * old RequiredPath.valueOf(...) logic.
     */
    private void preAssignExistingIds() {
        Map<String, RequiredPath> stepById = new HashMap<>();
        for (RequiredPath step : allSteps) {
            stepById.put(step.svgId(), step);
        }

        for (SvgPathCandidate candidate : candidates) {
            if (!candidate.isOriginallyRequiredId()) {
                continue;
            }

            String id = candidate.getOriginalId();
            if (id == null || id.isBlank()) {
                continue;
            }

            RequiredPath required = stepById.get(id);
            if (required != null) {
                assignInternal(required, candidate);
            }
        }
    }

    /**
     * Compute the list of steps that are not yet assigned.
     */
    private List<RequiredPath> computeMissingSteps() {
        List<RequiredPath> missing = new ArrayList<>();
        for (RequiredPath path : allSteps) {
            if (!path.isAssigned()) {
                missing.add(path);
            }
        }
        return missing;
    }

    /**
     * Get the current step (the RequiredPath being assigned).
     */
    public RequiredPath currentStep() {
        if (currentStepIndex < missingSteps.size()) {
            return missingSteps.get(currentStepIndex);
        }
        return null;
    }

    /**
     * Get the number of remaining steps.
     */
    public int remaining() {
        return missingSteps.size() - currentStepIndex;
    }

    /**
     * Get the total number of missing steps.
     */
    public int totalMissing() {
        return missingSteps.size();
    }

    /**
     * Get the current step number (1-based).
     */
    public int currentStepNumber() {
        return currentStepIndex + 1;
    }

    /**
     * Check if the wizard is complete.
     */
    public boolean isComplete() {
        return currentStepIndex >= missingSteps.size();
    }

    /**
     * Assign the current step to the given candidate and advance to the next
     * step.
     */
    public void assignCurrent(SvgPathCandidate candidate) {
        if (isComplete()) {
            throw new IllegalStateException("Wizard is already complete");
        }
        if (candidate == null) {
            throw new IllegalArgumentException("Candidate cannot be null");
        }
        if (candidate.isGreen()) {
            throw new IllegalArgumentException("Cannot assign to a green candidate");
        }

        RequiredPath current = currentStep();
        if (current == null) {
            throw new IllegalStateException("No current step");
        }

        assignInternal(current, candidate);
        currentStepIndex++;
    }

    /**
     * Internal assignment method that maintains two-way references.
     */
    private void assignInternal(RequiredPath required, SvgPathCandidate candidate) {
        if (required == null || candidate == null) {
            throw new IllegalArgumentException("required/candidate cannot be null");
        }

        // Clear old assignment if any
        if (required.getAssignedCandidate() != null) {
            required.getAssignedCandidate().setAssignedRequired(null);
        }
        if (candidate.getAssignedRequired() != null) {
            candidate.getAssignedRequired().setAssignedCandidate(null);
        }

        // Set new assignment
        required.setAssignedCandidate(candidate);
        candidate.setAssignedRequired(required);
    }

    /**
     * Get all candidates.
     */
    public List<SvgPathCandidate> getCandidates() {
        return candidates;
    }

    /**
     * Get all required paths (full list, includes already-assigned ones).
     */
    public List<RequiredPath> getAllSteps() {
        return allSteps;
    }

    /**
     * Helper method to get path ID or generate a default one.
     */
    private String getPathIdOrDefault(String id, int index) {
        if (id != null && !id.isEmpty()) {
            return id;
        }
        return "path_" + index;
    }
}
