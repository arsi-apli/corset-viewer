package sk.arsi.corset.export;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import sk.arsi.corset.model.Curve2D;
import sk.arsi.corset.model.PanelCurves;
import sk.arsi.corset.model.Pt;
import sk.arsi.corset.svg.SvgDocument;
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
    
    // Default dimensions when panel bounds cannot be computed
    private static final double DEFAULT_VIEWPORT_WIDTH = 1000.0;
    private static final double DEFAULT_VIEWPORT_HEIGHT = 1000.0;

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
        
        // Compute bounds from panels to set appropriate viewBox
        double[] bounds = computePanelBounds(panels);
        double minX = bounds[0];
        double minY = bounds[1];
        double maxX = bounds[2];
        double maxY = bounds[3];
        
        // Add some padding (10% on each side)
        double width = maxX - minX;
        double height = maxY - minY;
        double padding = Math.max(width, height) * 0.1;
        
        minX -= padding;
        minY -= padding;
        width += 2 * padding;
        height += 2 * padding;
        
        svgRoot.setAttribute("viewBox", String.format("%.2f %.2f %.2f %.2f", minX, minY, width, height));
        svgRoot.setAttribute("width", String.format("%.2f", width));
        svgRoot.setAttribute("height", String.format("%.2f", height));
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

    /**
     * Compute bounds [minX, minY, maxX, maxY] from all panels.
     */
    private static double[] computePanelBounds(List<PanelCurves> panels) {
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;

        for (PanelCurves panel : panels) {
            // Check all curves in the panel
            for (Curve2D curve : new Curve2D[]{
                panel.getTop(), panel.getBottom(), panel.getWaist(),
                panel.getSeamToPrevUp(), panel.getSeamToPrevDown(),
                panel.getSeamToNextUp(), panel.getSeamToNextDown()
            }) {
                if (curve == null || curve.getPoints() == null) {
                    continue;
                }
                for (Pt p : curve.getPoints()) {
                    if (p != null && Double.isFinite(p.getX()) && Double.isFinite(p.getY())) {
                        minX = Math.min(minX, p.getX());
                        minY = Math.min(minY, p.getY());
                        maxX = Math.max(maxX, p.getX());
                        maxY = Math.max(maxY, p.getY());
                    }
                }
            }
        }

        // Fallback if no valid bounds found
        if (!Double.isFinite(minX) || !Double.isFinite(minY) || 
            !Double.isFinite(maxX) || !Double.isFinite(maxY)) {
            return new double[]{0, 0, DEFAULT_VIEWPORT_WIDTH, DEFAULT_VIEWPORT_HEIGHT};
        }

        return new double[]{minX, minY, maxX, maxY};
    }

    /**
     * Export panels with notches and allowances to SVG file.
     * Creates a structured SVG with panel groups, each containing allowances and notches subgroups.
     * 
     * @param svgDocument Original SVG document (used for reference)
     * @param panels List of panels to export
     * @param outputFile Output SVG file
     * @param notchCount Number of notches per seam
     * @param notchLengthMm Length of each notch tick in mm
     * @param allowanceDistance Allowance distance in mm
     * @throws Exception if export fails
     */
    public static void exportWithNotches(
            SvgDocument svgDocument,
            List<PanelCurves> panels,
            File outputFile,
            int notchCount,
            double notchLengthMm,
            double allowanceDistance) throws Exception {
        
        if (panels == null || panels.isEmpty()) {
            throw new IllegalArgumentException("No panels to export");
        }

        // Generate notches for all panels
        List<PanelNotches> allNotches = NotchGenerator.generateAllNotches(panels, notchCount, notchLengthMm);

        // Create new SVG document
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.newDocument();

        // Create root SVG element
        Element svgRoot = doc.createElementNS(SVG_NAMESPACE, "svg");
        svgRoot.setAttribute("version", "1.1");
        svgRoot.setAttribute("xmlns", SVG_NAMESPACE);
        
        // Compute bounds from panels to set appropriate viewBox
        double[] bounds = computePanelBounds(panels);
        double minX = bounds[0];
        double minY = bounds[1];
        double maxX = bounds[2];
        double maxY = bounds[3];
        
        // Add some padding (10% on each side)
        double width = maxX - minX;
        double height = maxY - minY;
        double padding = Math.max(width, height) * 0.1;
        
        minX -= padding;
        minY -= padding;
        width += 2 * padding;
        height += 2 * padding;
        
        svgRoot.setAttribute("viewBox", String.format("%.2f %.2f %.2f %.2f", minX, minY, width, height));
        svgRoot.setAttribute("width", String.format("%.2f", width));
        svgRoot.setAttribute("height", String.format("%.2f", height));
        doc.appendChild(svgRoot);

        // Export each panel in its own group with subgroups for allowances and notches
        for (int i = 0; i < panels.size(); i++) {
            PanelCurves panel = panels.get(i);
            PanelNotches panelNotches = allNotches.get(i);
            
            // Create panel group
            Element panelGroup = doc.createElementNS(SVG_NAMESPACE, "g");
            panelGroup.setAttribute("id", panel.getPanelId().name() + "_PANEL");
            svgRoot.appendChild(panelGroup);
            
            // Export panel curves
            exportPanelCurvesToGroup(doc, panelGroup, panel);
            
            // Create and populate allowances subgroup
            Element allowancesGroup = doc.createElementNS(SVG_NAMESPACE, "g");
            allowancesGroup.setAttribute("id", panel.getPanelId().name() + "_ALLOWANCES");
            panelGroup.appendChild(allowancesGroup);
            exportPanelAllowancesToGroup(doc, allowancesGroup, panel, allowanceDistance);
            
            // Create and populate notches subgroup
            Element notchesGroup = doc.createElementNS(SVG_NAMESPACE, "g");
            notchesGroup.setAttribute("id", panel.getPanelId().name() + "_NOTCHES");
            panelGroup.appendChild(notchesGroup);
            exportNotchesToGroup(doc, notchesGroup, panelNotches);
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
     * Export all curves of a panel to a specific group.
     */
    private static void exportPanelCurvesToGroup(Document doc, Element group, PanelCurves panel) {
        // Export main curves
        if (panel.getTop() != null) {
            appendPath(doc, group, panel.getTop(), "black", 2.0);
        }
        if (panel.getBottom() != null) {
            appendPath(doc, group, panel.getBottom(), "black", 2.0);
        }
        if (panel.getWaist() != null) {
            appendPath(doc, group, panel.getWaist(), "black", 3.0);
        }

        // Export seam curves
        if (panel.getSeamToPrevUp() != null) {
            appendPath(doc, group, panel.getSeamToPrevUp(), "black", 1.5);
        }
        if (panel.getSeamToPrevDown() != null) {
            appendPath(doc, group, panel.getSeamToPrevDown(), "black", 1.5);
        }
        if (panel.getSeamToNextUp() != null) {
            appendPath(doc, group, panel.getSeamToNextUp(), "black", 1.5);
        }
        if (panel.getSeamToNextDown() != null) {
            appendPath(doc, group, panel.getSeamToNextDown(), "black", 1.5);
        }
    }

    /**
     * Export allowance offset curves for internal seams to a specific group.
     */
    private static void exportPanelAllowancesToGroup(Document doc, Element group, PanelCurves panel, double allowanceDistance) {
        exportSeamAllowanceToGroup(doc, group, panel.getSeamToPrevUp(), panel, allowanceDistance);
        exportSeamAllowanceToGroup(doc, group, panel.getSeamToPrevDown(), panel, allowanceDistance);
        exportSeamAllowanceToGroup(doc, group, panel.getSeamToNextUp(), panel, allowanceDistance);
        exportSeamAllowanceToGroup(doc, group, panel.getSeamToNextDown(), panel, allowanceDistance);
    }

    /**
     * Export allowance for a single seam to a specific group if it should have one.
     */
    private static void exportSeamAllowanceToGroup(Document doc, Element group, Curve2D seamCurve, PanelCurves panel, double allowanceDistance) {
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
        group.appendChild(path);
    }

    /**
     * Export notches to a specific group.
     */
    private static void exportNotchesToGroup(Document doc, Element group, PanelNotches panelNotches) {
        if (panelNotches == null || panelNotches.getNotches() == null) {
            return;
        }

        for (Notch notch : panelNotches.getNotches()) {
            Element path = doc.createElementNS(SVG_NAMESPACE, "path");
            path.setAttribute("id", notch.getId());
            
            // Create a simple line from start to end
            StringBuilder pathData = new StringBuilder();
            pathData.append("M ").append(notch.getStart().getX()).append(" ").append(notch.getStart().getY());
            pathData.append(" L ").append(notch.getEnd().getX()).append(" ").append(notch.getEnd().getY());
            
            path.setAttribute("d", pathData.toString());
            path.setAttribute("stroke", "black");
            path.setAttribute("stroke-width", "0.5");
            path.setAttribute("fill", "none");
            group.appendChild(path);
        }
    }

    /**
     * Export panels with allowances and notches to SVG by modifying the original SVG document.
     * This approach preserves the original SVG structure and styles.
     * 
     * @param svgDocument Original SVG document
     * @param panels List of panels to export
     * @param outputFile Output SVG file
     * @param notchCount Number of notches per seam
     * @param notchLengthMm Length of each notch tick in mm
     * @param allowanceDistance Allowance distance in mm
     * @throws Exception if export fails
     */
    public static void exportWithAllowancesAndNotches(
            SvgDocument svgDocument,
            List<PanelCurves> panels,
            File outputFile,
            int notchCount,
            double notchLengthMm,
            double allowanceDistance) throws Exception {
        
        if (svgDocument == null) {
            throw new IllegalArgumentException("SVG document is required");
        }
        if (panels == null || panels.isEmpty()) {
            throw new IllegalArgumentException("No panels to export");
        }

        // Clone the original document to avoid modifying it
        Document doc = (Document) svgDocument.getDocument().cloneNode(true);
        
        // Generate notches for all panels
        List<PanelNotches> allNotches = NotchGenerator.generateAllNotches(panels, notchCount, notchLengthMm);

        // For each panel, find or create container and add allowances/notches groups
        for (int i = 0; i < panels.size(); i++) {
            PanelCurves panel = panels.get(i);
            PanelNotches panelNotches = allNotches.get(i);
            String panelName = panel.getPanelId().name();
            
            // Find container element for this panel
            Element container = findPanelContainer(doc, svgDocument, panelName);
            
            if (container == null) {
                // Skip this panel if we can't find a container
                continue;
            }
            
            // Create or update allowances group
            Element allowancesGroup = findOrCreateGroup(doc, container, panelName + "_ALLOWANCES");
            clearElement(allowancesGroup);
            exportPanelAllowancesToGroup(doc, allowancesGroup, panel, allowanceDistance);
            
            // Create or update notches group
            Element notchesGroup = findOrCreateGroup(doc, container, panelName + "_NOTCHES");
            clearElement(notchesGroup);
            exportNotchesToGroup(doc, notchesGroup, panelNotches);
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
     * Find the container element for a panel.
     * First tries to find <PANEL>_PANEL group.
     * Falls back to finding <PANEL>_WAIST element and using its parent.
     */
    private static Element findPanelContainer(Document doc, SvgDocument svgDocument, String panelName) {
        // Try to find <PANEL>_PANEL group in the cloned document
        Element panelGroup = findElementByIdInDocument(doc, panelName + "_PANEL");
        if (panelGroup != null) {
            return panelGroup;
        }
        
        // Fallback: find <PANEL>_WAIST and use its parent
        Element waistElement = findElementByIdInDocument(doc, panelName + "_WAIST");
        if (waistElement != null && waistElement.getParentNode() instanceof Element) {
            return (Element) waistElement.getParentNode();
        }
        
        // Try other required elements as fallback
        String[] fallbackIds = {
            panelName + "_TOP",
            panelName + "_BOTTOM",
            panelName + "A_UP",
            panelName + "A_DOWN"
        };
        
        for (String fallbackId : fallbackIds) {
            Element element = findElementByIdInDocument(doc, fallbackId);
            if (element != null && element.getParentNode() instanceof Element) {
                return (Element) element.getParentNode();
            }
        }
        
        return null;
    }

    /**
     * Find element by ID in a document by traversing all elements.
     */
    private static Element findElementByIdInDocument(Document doc, String id) {
        return findElementByIdRecursive(doc.getDocumentElement(), id);
    }

    /**
     * Recursively search for element with given ID.
     */
    private static Element findElementByIdRecursive(Element root, String id) {
        if (root.hasAttribute("id") && id.equals(root.getAttribute("id"))) {
            return root;
        }
        
        NodeList children = root.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element) {
                Element found = findElementByIdRecursive((Element) child, id);
                if (found != null) {
                    return found;
                }
            }
        }
        
        return null;
    }

    /**
     * Find or create a group element with the given ID as a child of the container.
     */
    private static Element findOrCreateGroup(Document doc, Element container, String groupId) {
        // Check if group already exists as a direct child
        NodeList children = container.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element) {
                Element elem = (Element) child;
                if ("g".equals(elem.getLocalName()) && groupId.equals(elem.getAttribute("id"))) {
                    return elem;
                }
            }
        }
        
        // Create new group
        Element group = doc.createElementNS(SVG_NAMESPACE, "g");
        group.setAttribute("id", groupId);
        container.appendChild(group);
        return group;
    }

    /**
     * Remove all child nodes from an element.
     */
    private static void clearElement(Element element) {
        while (element.hasChildNodes()) {
            element.removeChild(element.getFirstChild());
        }
    }
    
    /**
     * Export resized SVG preserving original styles and only updating path coordinates.
     * 
     * @param svgDocument Original SVG document
     * @param originalPanels Original panels (before resize)
     * @param resizedPanels Resized panels
     * @param mode Resize mode
     * @param outputFile Output SVG file
     * @throws Exception if export fails
     */
    public static void exportResizedSvg(
            sk.arsi.corset.svg.SvgDocument svgDocument,
            java.util.List<sk.arsi.corset.model.PanelCurves> originalPanels,
            java.util.List<sk.arsi.corset.resize.ResizedPanel> resizedPanels,
            sk.arsi.corset.resize.ResizeMode mode,
            java.io.File outputFile) throws Exception {
        
        if (svgDocument == null) {
            throw new IllegalArgumentException("SVG document is required");
        }
        if (originalPanels == null || originalPanels.isEmpty()) {
            throw new IllegalArgumentException("No panels to export");
        }
        if (resizedPanels == null || resizedPanels.size() != originalPanels.size()) {
            throw new IllegalArgumentException("Resized panels size mismatch");
        }
        
        // Clone the original document to avoid modifying it
        org.w3c.dom.Document doc = (org.w3c.dom.Document) svgDocument.getDocument().cloneNode(true);
        
        // For each panel, update the path 'd' attributes based on mode
        for (int i = 0; i < resizedPanels.size(); i++) {
            sk.arsi.corset.resize.ResizedPanel resized = resizedPanels.get(i);
            sk.arsi.corset.model.PanelCurves original = originalPanels.get(i);
            String panelName = original.getPanelId().name();
            
            // Update curves based on mode
            switch (mode) {
                case GLOBAL:
                    // Update all curves
                    updatePathInDocument(doc, panelName + "_TOP", resized.getTop());
                    updatePathInDocument(doc, panelName + "_BOTTOM", resized.getBottom());
                    updatePathInDocument(doc, panelName + "_WAIST", resized.getWaist());
                    updatePathInDocument(doc, getSeamId(panelName, true, true), resized.getSeamToPrevUp());
                    updatePathInDocument(doc, getSeamId(panelName, true, false), resized.getSeamToPrevDown());
                    updatePathInDocument(doc, getSeamId(panelName, false, true), resized.getSeamToNextUp());
                    updatePathInDocument(doc, getSeamId(panelName, false, false), resized.getSeamToNextDown());
                    break;
                    
                case TOP:
                    // Update only TOP curve and UP seams
                    updatePathInDocument(doc, panelName + "_TOP", resized.getTop());
                    updatePathInDocument(doc, getSeamId(panelName, true, true), resized.getSeamToPrevUp());
                    updatePathInDocument(doc, getSeamId(panelName, false, true), resized.getSeamToNextUp());
                    break;
                    
                case BOTTOM:
                    // Update only BOTTOM curve and DOWN seams
                    updatePathInDocument(doc, panelName + "_BOTTOM", resized.getBottom());
                    updatePathInDocument(doc, getSeamId(panelName, true, false), resized.getSeamToPrevDown());
                    updatePathInDocument(doc, getSeamId(panelName, false, false), resized.getSeamToNextDown());
                    break;
            }
        }
        
        // Write to file
        javax.xml.transform.TransformerFactory transformerFactory = javax.xml.transform.TransformerFactory.newInstance();
        javax.xml.transform.Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(javax.xml.transform.OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        transformer.setOutputProperty(javax.xml.transform.OutputKeys.ENCODING, "UTF-8");
        
        javax.xml.transform.dom.DOMSource source = new javax.xml.transform.dom.DOMSource(doc);
        javax.xml.transform.stream.StreamResult result = new javax.xml.transform.stream.StreamResult(outputFile);
        transformer.transform(source, result);
    }
    
    /**
     * Get the seam ID for a panel based on naming convention.
     * Seams are named like AA_UP, AB_DOWN, etc.
     */
    private static String getSeamId(String panelName, boolean isPrev, boolean isUp) {
        // For seamToPrev, the seam ID is <panelName>A_<UP|DOWN>
        // For seamToNext, the seam ID is <panelName>B_<UP|DOWN>
        // This is a simplification; actual seam IDs may vary
        String suffix = isPrev ? "A" : "B";
        String direction = isUp ? "_UP" : "_DOWN";
        return panelName + suffix + direction;
    }
    
    /**
     * Update a path element's 'd' attribute in the document.
     */
    private static void updatePathInDocument(org.w3c.dom.Document doc, String pathId, sk.arsi.corset.model.Curve2D curve) {
        if (curve == null) {
            return;
        }
        
        org.w3c.dom.Element pathElement = findElementByIdInDocument(doc, pathId);
        if (pathElement == null) {
            // Path not found in document, skip
            return;
        }
        
        // Update the 'd' attribute with new path data
        String newPathData = pointsToPathData(curve.getPoints());
        pathElement.setAttribute("d", newPathData);
    }
}
