package sk.arsi.corset.export;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import sk.arsi.corset.model.PanelCurves;
import sk.arsi.corset.model.PanelId;
import sk.arsi.corset.svg.SvgDocument;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.util.List;

/**
 * Service for exporting panels with seam allowances and notches to SVG.
 */
public final class SvgExporter {

    /**
     * Export panels with allowances and notches to an SVG file.
     * 
     * @param originalDoc The original SVG document
     * @param panels The panel curves
     * @param outputFile The output SVG file path
     * @param notchCount Number of notches per seam
     * @param notchLengthMm Length of each notch in mm
     * @throws Exception if export fails
     */
    public static void exportWithNotches(
            SvgDocument originalDoc,
            List<PanelCurves> panels,
            File outputFile,
            int notchCount,
            double notchLengthMm) throws Exception {
        
        // Clone the document
        Document doc = (Document) originalDoc.getDocument().cloneNode(true);
        
        // Generate notches for all panels
        List<PanelNotches> allNotches = NotchGenerator.generateAllNotches(
                panels, notchCount, notchLengthMm);
        
        // Add notches to the document
        addNotchesToDocument(doc, allNotches);
        
        // Write the document to file
        writeDocument(doc, outputFile);
    }

    /**
     * Add notches to the SVG document.
     * For each panel, find or create a panel group and add a notches subgroup.
     */
    private static void addNotchesToDocument(Document doc, List<PanelNotches> allNotches) {
        Element root = doc.getDocumentElement();
        
        for (PanelNotches panelNotches : allNotches) {
            PanelId panelId = panelNotches.getPanelId();
            List<Notch> notches = panelNotches.getNotches();
            
            if (notches.isEmpty()) {
                continue;
            }
            
            // Find or create panel group
            Element panelGroup = findOrCreatePanelGroup(doc, root, panelId);
            
            // Remove existing notches group if present
            removeNotchesGroup(panelGroup, panelId);
            
            // Create new notches group
            Element notchesGroup = doc.createElement("g");
            notchesGroup.setAttribute("id", panelId.name() + "_NOTCHES");
            notchesGroup.setAttribute("stroke", "#0000FF");
            notchesGroup.setAttribute("stroke-width", "0.5");
            notchesGroup.setAttribute("fill", "none");
            
            // Add each notch as a path
            for (Notch notch : notches) {
                Element path = doc.createElement("path");
                path.setAttribute("id", notch.getId());
                
                String d = String.format("M %.3f %.3f L %.3f %.3f",
                        notch.getStart().getX(), notch.getStart().getY(),
                        notch.getEnd().getX(), notch.getEnd().getY());
                path.setAttribute("d", d);
                
                notchesGroup.appendChild(path);
            }
            
            // Add notches group to panel group
            panelGroup.appendChild(notchesGroup);
        }
    }

    /**
     * Find or create a group element for a panel.
     * If the panel paths are already in a group, use that.
     * Otherwise, create a new group and move panel paths into it.
     */
    private static Element findOrCreatePanelGroup(Document doc, Element root, PanelId panelId) {
        String groupId = panelId.name() + "_PANEL";
        
        // Try to find existing panel group
        Element existingGroup = findElementByIdRecursive(root, groupId);
        if (existingGroup != null && "g".equals(existingGroup.getTagName())) {
            return existingGroup;
        }
        
        // Create new group
        Element panelGroup = doc.createElement("g");
        panelGroup.setAttribute("id", groupId);
        
        // Move panel paths into the group
        movePanelPathsToGroup(doc, root, panelGroup, panelId);
        
        // Add group to root
        root.appendChild(panelGroup);
        
        return panelGroup;
    }

    /**
     * Move all paths belonging to a panel into the panel group.
     */
    private static void movePanelPathsToGroup(Document doc, Element root, Element panelGroup, PanelId panelId) {
        String prefix = panelId.name();
        
        // List of path IDs to move
        String[] pathIds = {
                prefix + "_TOP",
                prefix + "_BOTTOM",
                prefix + "_WAIST",
                prefix + prefix + "_UP",  // e.g., AA_UP
                prefix + prefix + "_DOWN",
                prefix + getPrevId(panelId) + "_UP",
                prefix + getPrevId(panelId) + "_DOWN",
                prefix + getNextId(panelId) + "_UP",
                prefix + getNextId(panelId) + "_DOWN"
        };
        
        for (String pathId : pathIds) {
            Element pathElem = findElementByIdRecursive(root, pathId);
            if (pathElem != null && "path".equals(pathElem.getTagName())) {
                // Move to panel group
                Node parent = pathElem.getParentNode();
                if (parent != null && parent != panelGroup) {
                    parent.removeChild(pathElem);
                    panelGroup.appendChild(pathElem);
                }
            }
        }
    }

    /**
     * Remove existing notches group from panel group if present.
     */
    private static void removeNotchesGroup(Element panelGroup, PanelId panelId) {
        String notchesId = panelId.name() + "_NOTCHES";
        Element existing = findChildById(panelGroup, notchesId);
        if (existing != null) {
            panelGroup.removeChild(existing);
        }
    }

    /**
     * Find an element by ID recursively.
     */
    private static Element findElementByIdRecursive(Element parent, String id) {
        if (id.equals(parent.getAttribute("id"))) {
            return parent;
        }
        
        org.w3c.dom.NodeList children = parent.getChildNodes();
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
     * Find a direct child element by ID.
     */
    private static Element findChildById(Element parent, String id) {
        org.w3c.dom.NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element) {
                Element elem = (Element) child;
                if (id.equals(elem.getAttribute("id"))) {
                    return elem;
                }
            }
        }
        return null;
    }

    /**
     * Write the document to a file.
     */
    private static void writeDocument(Document doc, File outputFile) throws Exception {
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.METHOD, "xml");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        
        DOMSource source = new DOMSource(doc);
        StreamResult result = new StreamResult(outputFile);
        transformer.transform(source, result);
    }

    private static String getPrevId(PanelId id) {
        switch (id) {
            case A: return "A";
            case B: return "A";
            case C: return "B";
            case D: return "C";
            case E: return "D";
            case F: return "E";
            default: return "";
        }
    }

    private static String getNextId(PanelId id) {
        switch (id) {
            case A: return "B";
            case B: return "C";
            case C: return "D";
            case D: return "E";
            case E: return "F";
            case F: return "F";
            default: return "";
        }
    }
}
