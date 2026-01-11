package sk.arsi.corset.svg;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import org.apache.batik.util.XMLResourceDescriptor;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.io.File;
import java.io.IOException;
import java.util.*;
import org.apache.batik.anim.dom.SAXSVGDocumentFactory;

/**
 * Utilities to detect (and optionally report) SVG group transforms that affect
 * the path elements we care about (seams/edges).
 *
 * Usage: - Parse the SVG file with parseSvgFile(...) - Build a set of relevant
 * path ids (the ids you use in the app, e.g. from PanelCurves) - Call
 * findPathsWithAncestorTransforms(doc, relevantIds) to get map pathId ->
 * ancestor transform string - Optionally call promptUserIfTransforms(...) from
 * JavaFX thread to show a warning dialog (dialog returns true when user chooses
 * to apply transforms / accept; false otherwise)
 *
 * Important: this class only *detects* transform attributes on ancestor groups
 * of the specified path elements. It does not modify SVG content. Applying
 * transforms (i.e. baking transforms into path 'd' attributes) is a separate
 * step and may require converting curved segments; that must be done carefully
 * to avoid flattening artifacts.
 */
public final class SvgTransformChecker {

    private SvgTransformChecker() {
    }

    /**
     * Parse SVG file into W3C Document using Batik SAXSVGDocumentFactory.
     */
    public static Document parseSvgFile(File svgFile) throws IOException {
        String parser = XMLResourceDescriptor.getXMLParserClassName();
        SAXSVGDocumentFactory f = new SAXSVGDocumentFactory(parser);
        String uri = svgFile.toURI().toString();
        return f.createDocument(uri);
    }

    /**
     * For each path id in relevantPathIds, find the nearest ancestor element
     * (including parent groups) that has a non-empty 'transform' attribute.
     * Returns a map of pathId -> transform description.
     *
     * If path id not present in document it is omitted from the result.
     */
    public static Map<String, String> findPathsWithAncestorTransforms(Document doc, Set<String> relevantPathIds) {
        if (doc == null || relevantPathIds == null || relevantPathIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, String> found = new LinkedHashMap<>();
        for (String id : relevantPathIds) {
            try {
                org.w3c.dom.Element pathEl = doc.getElementById(id);
                if (pathEl == null) {
                    // try searching by attribute id if DOM ID mechanism is not wired
                    pathEl = findElementByIdAttr(doc, id);
                }
                if (pathEl == null) {
                    continue;
                }

                Element anc = findAncestorWithTransform(pathEl);
                if (anc != null) {
                    String transform = anc.getAttribute("transform");
                    String ancId = anc.getAttribute("id");
                    String desc = (ancId != null && !ancId.isBlank()) ? ("ancestor id='" + ancId + "' transform=\"" + transform + "\"")
                            : ("ancestor tag='" + anc.getTagName() + "' transform=\"" + transform + "\"");
                    found.put(id, desc);
                }
            } catch (Throwable ignored) {
                // ignore individual failures: continue checking other ids
            }
        }
        return found;
    }

    /**
     * Find nearest ancestor element (excluding the element itself) that has a
     * non-empty transform attribute. Returns null if none found.
     */
    private static Element findAncestorWithTransform(Element el) {
        Node p = el.getParentNode();
        while (p != null && p.getNodeType() == Node.ELEMENT_NODE) {
            Element e = (Element) p;
            if (e.hasAttribute("transform") && !e.getAttribute("transform").trim().isEmpty()) {
                return e;
            }
            p = p.getParentNode();
        }
        return null;
    }

    /**
     * Fallback search for an element with the given id attribute (some SVG
     * sources do not register ids with the DOM's getElementById).
     */
    private static Element findElementByIdAttr(Document doc, String id) {
        // naive tree walk - ok for single-use initial analysis
        return findElementByIdAttrRecursive(doc.getDocumentElement(), id);
    }

    private static Element findElementByIdAttrRecursive(Element el, String id) {
        if (el == null) {
            return null;
        }
        if (id.equals(el.getAttribute("id"))) {
            return el;
        }
        for (Node n = el.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n.getNodeType() == Node.ELEMENT_NODE) {
                Element found = findElementByIdAttrRecursive((Element) n, id);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /**
     * Convenience: build Set of relevant path ids from PanelCurves list. This
     * collects typical ids used by the app (top, bottom, waist, seam*).
     */
    public static Set<String> collectRelevantPathIdsFromPanels(List<?> panelCurvesList) {
        if (panelCurvesList == null) {
            return Collections.emptySet();
        }
        Set<String> ids = new LinkedHashSet<>();
        for (Object obj : panelCurvesList) {
            if (!(obj instanceof sk.arsi.corset.model.PanelCurves)) {
                continue;
            }
            sk.arsi.corset.model.PanelCurves p = (sk.arsi.corset.model.PanelCurves) obj;
            addIfId(ids, p.getTop());
            addIfId(ids, p.getBottom());
            addIfId(ids, p.getWaist());
            addIfId(ids, p.getSeamToPrevUp());
            addIfId(ids, p.getSeamToPrevDown());
            addIfId(ids, p.getSeamToNextUp());
            addIfId(ids, p.getSeamToNextDown());
        }
        return ids;
    }

    private static void addIfId(Set<String> ids, sk.arsi.corset.model.Curve2D c) {
        if (c == null) {
            return;
        }
        String id = c.getId();
        if (id != null && !id.isBlank()) {
            ids.add(id);
        }
    }

    /**
     * JavaFX-friendly prompt. If any relevant path has an ancestor transform
     * the method shows a blocking confirmation dialog on the JavaFX thread and
     * returns the user's choice:
     *
     * - TRUE = user chose "Apply transforms" (caller should proceed to
     * apply/bake transforms) - FALSE = user chose "Ignore" or closed dialog
     * (caller should not apply transforms)
     *
     * Must be called from JavaFX Application Thread, or the dialog will be
     * shown via Platform.runLater.
     *
     * The dialog lists affected path ids and the ancestor transform
     * descriptions.
     */
    public static void promptUserIfTransforms(Document doc, Set<String> relevantPathIds, java.util.function.Consumer<Boolean> resultConsumer) {
        if (doc == null || relevantPathIds == null || relevantPathIds.isEmpty()) {
            resultConsumer.accept(false);
            return;
        }

        Map<String, String> found = findPathsWithAncestorTransforms(doc, relevantPathIds);
        if (found.isEmpty()) {
            resultConsumer.accept(false);
            return;
        }

        Runnable show = () -> {
            String title = "SVG group transforms detected";
            String header = "Some path elements are inside transformed <g> groups.";
            String body = "Corset-viewer will not work properly! Return to Inkscape and  remove/apply transforms via: Extensions → Modify Path → Apply Transform";
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle(title);
            alert.setHeaderText(header);
            alert.setContentText(body);
            ButtonType applyBtn = new ButtonType("Exit", ButtonBar.ButtonData.YES);
            ButtonType ignoreBtn = new ButtonType("Ignore", ButtonBar.ButtonData.NO);
            alert.getButtonTypes().setAll(applyBtn, ignoreBtn);

            Optional<ButtonType> opt = alert.showAndWait();
            boolean apply = opt.isPresent() && opt.get() == applyBtn;
            resultConsumer.accept(apply);
        };

        if (Platform.isFxApplicationThread()) {
            show.run();
        } else {
            Platform.runLater(show);
        }
    }
}
