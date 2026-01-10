package sk.arsi.corset.svg;

import org.w3c.dom.Element;
import sk.arsi.corset.model.Curve2D;
import sk.arsi.corset.model.PanelCurves;
import sk.arsi.corset.model.PanelId;

import java.util.ArrayList;
import java.util.List;

public final class PatternExtractor {

    private final PatternContract contract;
    private final PathSampler sampler;

    public PatternExtractor(PatternContract contract, PathSampler sampler) {
        this.contract = contract;
        this.sampler = sampler;
    }

    public List<PanelCurves> extractPanels(SvgDocument doc, double flatnessMm) {
        return extractPanels(doc, flatnessMm, 0.0);
    }

    public List<PanelCurves> extractPanels(SvgDocument doc, double flatnessMm, double resampleStepMm) {
        List<PanelCurves> out = new ArrayList<>();

        // Previously hardcoded A..F. Now driven by the contract maxPanel (default F).
        List<PanelId> panels = PanelId.rangeInclusive(contract.getMaxPanel());

        for (PanelId panelId : panels) {
            String topId = contract.topId(panelId);
            String bottomId = contract.bottomId(panelId);
            String waistId = contract.waistId(panelId);

            Curve2D top = readPath(doc, topId, flatnessMm, resampleStepMm);
            Curve2D bottom = readPath(doc, bottomId, flatnessMm, resampleStepMm);
            Curve2D waist = readPath(doc, waistId, flatnessMm, resampleStepMm);

            // seams: "left seam to prev" + "right seam to next"
            String seamToPrevId = contract.seamToPrevId(panelId);
            String seamToNextId = contract.seamToNextId(panelId);

            Curve2D seamToPrevUp = readPath(doc, contract.seamUpId(seamToPrevId), flatnessMm, resampleStepMm);
            Curve2D seamToPrevDown = readPath(doc, contract.seamDownId(seamToPrevId), flatnessMm, resampleStepMm);

            Curve2D seamToNextUp = readPath(doc, contract.seamUpId(seamToNextId), flatnessMm, resampleStepMm);
            Curve2D seamToNextDown = readPath(doc, contract.seamDownId(seamToNextId), flatnessMm, resampleStepMm);

            PanelCurves panel = new PanelCurves(
                    panelId,
                    top,
                    bottom,
                    waist,
                    seamToPrevUp,
                    seamToPrevDown,
                    seamToNextUp,
                    seamToNextDown
            );

            out.add(panel);
        }

        return out;
    }

    private Curve2D readPath(SvgDocument doc, String id, double flatnessMm, double resampleStepMm) {
        Element element = doc.getRequiredElement(id);
        String tagName = element.getTagName();
        if (!"path".equals(tagName)) {
            throw new IllegalStateException("Element id=" + id + " must be <path>, but is <" + tagName + ">");
        }
        String d = element.getAttribute("d");
        return sampler.samplePath(id, d, flatnessMm, resampleStepMm);
    }
}
