package sk.arsi.corset.export;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import sk.arsi.corset.model.Curve2D;
import sk.arsi.corset.model.PanelCurves;
import sk.arsi.corset.model.Pt;
import sk.arsi.corset.util.SeamAllowanceComputer;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.util.List;

/**
 * Exports panel curves with seam allowances to SVG.
 */
public final class SvgExporter {

    private static final String SVG_NAMESPACE = "http://www.w3.org/2000/svg";

    private SvgExporter() {
        // utility class
    }

    /**
     * Export panels with allowances to an SVG file.
     *
     * @param panels List of panels to export
     * @param allowanceDistance Allowance distance in mm
     * @param outputFile Output SVG file
     * @throws Exception if export fails
     */
    public static void exportWithAllowances(List<PanelCurves> panels, double allowanceDistance, File outputFile) throws Exception {
        if (panels == null || panels.isEmpty()) {
            throw new IllegalArgumentException("No panels to export");
        }

        // Create new SVG document
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.newDocument();

        // Create root SVG element
        Element svgRoot = doc.createElementNS(SVG_NAMESPACE, "svg");
        svgRoot.setAttribute("version", "1.1");
        svgRoot.setAttribute("xmlns", SVG_NAMESPACE);
        
        // Set viewBox based on panel bounds (simple heuristic)
        svgRoot.setAttribute("viewBox", "0 0 1000 1000");
        svgRoot.setAttribute("width", "1000");
        svgRoot.setAttribute("height", "1000");
        doc.appendChild(svgRoot);

        // Create layers
        Element panelsLayer = doc.createElementNS(SVG_NAMESPACE, "g");
        panelsLayer.setAttribute("id", "panels");
        svgRoot.appendChild(panelsLayer);

        Element allowancesLayer = doc.createElementNS(SVG_NAMESPACE, "g");
        allowancesLayer.setAttribute("id", "allowances");
        svgRoot.appendChild(allowancesLayer);

        // Export each panel's curves
        for (PanelCurves panel : panels) {
            exportPanelCurves(doc, panelsLayer, panel);
            exportPanelAllowances(doc, allowancesLayer, panel, allowanceDistance);
        }

        // Write to file
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");

        DOMSource source = new DOMSource(doc);
        StreamResult result = new StreamResult(outputFile);
        transformer.transform(source, result);
    }

    /**
     * Export all curves of a panel to the panels layer.
     */
    private static void exportPanelCurves(Document doc, Element layer, PanelCurves panel) {
        // Export main curves
        if (panel.getTop() != null) {
            appendPath(doc, layer, panel.getTop(), "black", 2.0);
        }
        if (panel.getBottom() != null) {
            appendPath(doc, layer, panel.getBottom(), "black", 2.0);
        }
        if (panel.getWaist() != null) {
            appendPath(doc, layer, panel.getWaist(), "black", 3.0);
        }

        // Export seam curves
        if (panel.getSeamToPrevUp() != null) {
            appendPath(doc, layer, panel.getSeamToPrevUp(), "black", 1.5);
        }
        if (panel.getSeamToPrevDown() != null) {
            appendPath(doc, layer, panel.getSeamToPrevDown(), "black", 1.5);
        }
        if (panel.getSeamToNextUp() != null) {
            appendPath(doc, layer, panel.getSeamToNextUp(), "black", 1.5);
        }
        if (panel.getSeamToNextDown() != null) {
            appendPath(doc, layer, panel.getSeamToNextDown(), "black", 1.5);
        }
    }

    /**
     * Export allowance offset curves for internal seams only.
     */
    private static void exportPanelAllowances(Document doc, Element layer, PanelCurves panel, double allowanceDistance) {
        exportSeamAllowance(doc, layer, panel.getSeamToPrevUp(), panel, allowanceDistance);
        exportSeamAllowance(doc, layer, panel.getSeamToPrevDown(), panel, allowanceDistance);
        exportSeamAllowance(doc, layer, panel.getSeamToNextUp(), panel, allowanceDistance);
        exportSeamAllowance(doc, layer, panel.getSeamToNextDown(), panel, allowanceDistance);
    }

    /**
     * Export allowance for a single seam if it should have one.
     */
    private static void exportSeamAllowance(Document doc, Element layer, Curve2D seamCurve, PanelCurves panel, double allowanceDistance) {
        if (seamCurve == null) {
            return;
        }

        String seamId = seamCurve.getId();
        if (!SeamAllowanceComputer.shouldGenerateAllowance(seamId)) {
            return;
        }

        // Compute offset curve
        List<Pt> offsetPoints = SeamAllowanceComputer.computeOffsetCurve(seamCurve, panel, allowanceDistance);
        if (offsetPoints == null || offsetPoints.size() < 2) {
            return;
        }

        // Create path element for allowance
        Element path = doc.createElementNS(SVG_NAMESPACE, "path");
        path.setAttribute("id", seamId + "_ALLOW");
        path.setAttribute("d", pointsToPathData(offsetPoints));
        path.setAttribute("stroke", "green");
        path.setAttribute("stroke-width", "1");
        path.setAttribute("fill", "none");
        layer.appendChild(path);
    }

    /**
     * Append a path element for a curve.
     */
    private static void appendPath(Document doc, Element layer, Curve2D curve, String stroke, double strokeWidth) {
        if (curve == null || curve.getPoints() == null || curve.getPoints().size() < 2) {
            return;
        }

        Element path = doc.createElementNS(SVG_NAMESPACE, "path");
        path.setAttribute("id", curve.getId());
        path.setAttribute("d", pointsToPathData(curve.getPoints()));
        path.setAttribute("stroke", stroke);
        path.setAttribute("stroke-width", String.valueOf(strokeWidth));
        path.setAttribute("fill", "none");
        layer.appendChild(path);
    }

    /**
     * Convert a list of points to SVG path data (M x y L x y L x y ...).
     */
    private static String pointsToPathData(List<Pt> points) {
        if (points == null || points.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        Pt first = points.get(0);
        if (first != null) {
            sb.append("M ").append(first.getX()).append(" ").append(first.getY());
        }

        for (int i = 1; i < points.size(); i++) {
            Pt p = points.get(i);
            if (p != null) {
                sb.append(" L ").append(p.getX()).append(" ").append(p.getY());
            }
        }

        return sb.toString();
    }
}
