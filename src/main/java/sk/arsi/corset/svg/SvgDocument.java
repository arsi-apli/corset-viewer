package sk.arsi.corset.svg;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.Collections;
import java.util.Map;

public final class SvgDocument {

    private final Document document;
    private final Map<String, Element> elementsById;

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
}
