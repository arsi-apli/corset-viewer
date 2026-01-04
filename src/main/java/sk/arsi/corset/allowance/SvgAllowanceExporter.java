package sk.arsi.corset.allowance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import sk.arsi.corset.model.Curve2D;
import sk.arsi.corset.model.PanelCurves;
import sk.arsi.corset.model.Pt;
import sk.arsi.corset.svg.PatternContract;
import sk.arsi.corset.svg.SvgDocument;
import sk.arsi.corset.svg.SvgLoader;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Exports SVG with seam allowance paths added to an "allowances" layer.
 */
public final class SvgAllowanceExporter {

    private static final Logger LOG = LoggerFactory.getLogger(SvgAllowanceExporter.class);
    private static final String ALLOWANCES_GROUP_ID = "allowances";
    private static final String INKSCAPE_NS = "http://www.inkscape.org/namespaces/inkscape";
    
    private final SeamAllowanceGenerator generator;
    private final PatternContract contract;

    public SvgAllowanceExporter() {
        this.generator = new SeamAllowanceGenerator();
        this.contract = new PatternContract();
    }

    /**
     * Export SVG with allowances for all vertical seams in the panels.
     * 
     * @param inputPath Input SVG path
     * @param outputPath Output SVG path
     * @param panels List of panel curves
     * @param allowanceMm Allowance in mm
     * @throws IOException if file operations fail
     */
    public void export(Path inputPath, Path outputPath, List<PanelCurves> panels, double allowanceMm) throws IOException {
        // Load the original SVG
        SvgLoader loader = new SvgLoader();
        SvgDocument svgDoc = loader.load(inputPath);
        Document doc = svgDoc.getDocument();

        // Generate allowance curves
        List<Curve2D> allowanceCurves = generateAllowanceCurves(panels, allowanceMm);

        // Add or update allowances layer
        addAllowancesLayer(doc, allowanceCurves);

        // Write to output file
        writeDocument(doc, outputPath);
        
        LOG.info("Exported SVG with {} allowance curves to {}", allowanceCurves.size(), outputPath);
    }

    /**
     * Generate allowance curves for all vertical seams in all panels.
     */
    private List<Curve2D> generateAllowanceCurves(List<PanelCurves> panels, double allowanceMm) {
        List<Curve2D> allowances = new ArrayList<>();

        for (PanelCurves panel : panels) {
            // Left seam (seamToPrev): offset to the left (outside of panel)
            Curve2D leftUpAllowance = generator.generateOffset(panel.getSeamToPrevUp(), allowanceMm, true);
            allowances.add(leftUpAllowance);
            
            Curve2D leftDownAllowance = generator.generateOffset(panel.getSeamToPrevDown(), allowanceMm, true);
            allowances.add(leftDownAllowance);

            // Right seam (seamToNext): offset to the right (outside of panel)
            Curve2D rightUpAllowance = generator.generateOffset(panel.getSeamToNextUp(), allowanceMm, false);
            allowances.add(rightUpAllowance);
            
            Curve2D rightDownAllowance = generator.generateOffset(panel.getSeamToNextDown(), allowanceMm, false);
            allowances.add(rightDownAllowance);
        }

        return allowances;
    }

    /**
     * Add or update the allowances layer in the SVG document.
     */
    private void addAllowancesLayer(Document doc, List<Curve2D> allowanceCurves) {
        Element root = doc.getDocumentElement();

        // Find or create allowances group
        Element allowancesGroup = findOrCreateAllowancesGroup(doc, root);

        // Clear existing content in the group
        while (allowancesGroup.hasChildNodes()) {
            allowancesGroup.removeChild(allowancesGroup.getFirstChild());
        }

        // Add allowance paths
        for (Curve2D curve : allowanceCurves) {
            Element path = createPathElement(doc, curve);
            allowancesGroup.appendChild(path);
        }
    }

    /**
     * Find existing allowances group or create a new one.
     */
    private Element findOrCreateAllowancesGroup(Document doc, Element root) {
        // Look for existing group with id="allowances"
        NodeList groups = root.getElementsByTagName("g");
        for (int i = 0; i < groups.getLength(); i++) {
            Element g = (Element) groups.item(i);
            if (ALLOWANCES_GROUP_ID.equals(g.getAttribute("id"))) {
                return g;
            }
        }

        // Create new group
        Element group = doc.createElement("g");
        group.setAttribute("id", ALLOWANCES_GROUP_ID);
        group.setAttributeNS(INKSCAPE_NS, "inkscape:label", "Allowances");
        group.setAttributeNS(INKSCAPE_NS, "inkscape:groupmode", "layer");
        root.appendChild(group);
        
        return group;
    }

    /**
     * Create a path element for an allowance curve.
     */
    private Element createPathElement(Document doc, Curve2D curve) {
        Element path = doc.createElement("path");
        path.setAttribute("id", curve.getId());
        path.setAttribute("d", generatePathData(curve));
        path.setAttribute("style", "fill:none;stroke:#00ff00;stroke-width:1");
        return path;
    }

    /**
     * Generate SVG path data from curve points.
     */
    private String generatePathData(Curve2D curve) {
        List<Pt> points = curve.getPoints();
        if (points.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        
        // Move to first point
        Pt first = points.get(0);
        sb.append("M ").append(formatCoord(first.getX())).append(",").append(formatCoord(first.getY()));

        // Line to subsequent points
        for (int i = 1; i < points.size(); i++) {
            Pt pt = points.get(i);
            sb.append(" L ").append(formatCoord(pt.getX())).append(",").append(formatCoord(pt.getY()));
        }

        return sb.toString();
    }

    /**
     * Format coordinate value for SVG.
     */
    private String formatCoord(double value) {
        // Use reasonable precision
        return String.format("%.3f", value);
    }

    /**
     * Write document to file.
     */
    private void writeDocument(Document doc, Path outputPath) throws IOException {
        try {
            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer transformer = tf.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.METHOD, "xml");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            
            DOMSource source = new DOMSource(doc);
            StreamResult result = new StreamResult(outputPath.toFile());
            transformer.transform(source, result);
        } catch (Exception e) {
            throw new IOException("Failed to write SVG document", e);
        }
    }
}
