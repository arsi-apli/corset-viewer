package sk.arsi.corset.svg;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

public final class SvgDocument {

    private final Document document;
    private final Map<String, Element> elementsById;
    
    /**
     * Attribute name for max panel metadata on SVG root element.
     */
    public static final String ATTR_MAX_PANEL = "data-corset-panels-max";

    public SvgDocument(Document document, Map<String, Element> elementsById) {
        this.document = document;
        this.elementsById = Collections.unmodifiableMap(elementsById);
    }

    public Document getDocument() {
        return document;
    }

    public Map<String, Element> getElementsById() {
        return elementsById;
    }

    public Element getRequiredElement(String id) {
        Element element = elementsById.get(id);
        if (element == null) {
            throw new IllegalStateException("Missing required SVG element id=" + id);
        }
        return element;
    }
    
    /**
     * Read optional panel count metadata from SVG root element.
     * 
     * @return Optional containing the max panel letter (uppercase A-Z) if present and valid, empty otherwise
     */
    public Optional<Character> readMaxPanelMetadata() {
        Element root = document.getDocumentElement();
        if (root == null) {
            return Optional.empty();
        }
        
        String value = root.getAttribute(ATTR_MAX_PANEL);
        if (value == null || value.isEmpty()) {
            return Optional.empty();
        }
        
        value = value.trim();
        if (value.length() != 1) {
            return Optional.empty();
        }
        
        char ch = Character.toUpperCase(value.charAt(0));
        if (ch >= 'A' && ch <= 'Z') {
            return Optional.of(ch);
        }
        
        return Optional.empty();
    }
}
