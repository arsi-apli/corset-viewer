package sk.arsi.corset.svg;

import org.apache.batik.anim.dom.SAXSVGDocumentFactory;
import org.apache.batik.util.XMLResourceDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

public final class SvgLoader {

    private static final Logger LOG = LoggerFactory.getLogger(SvgLoader.class);

    public SvgDocument load(Path svgFile) throws IOException {
        String parser = XMLResourceDescriptor.getXMLParserClassName();
        SAXSVGDocumentFactory factory = new SAXSVGDocumentFactory(parser);
        Document document = factory.createDocument(svgFile.toUri().toString());

        Map<String, Element> byId = new HashMap<String, Element>();
        Element root = document.getDocumentElement();

        Deque<Node> stack = new ArrayDeque<Node>();
        stack.push(root);

        while (!stack.isEmpty()) {
            Node node = stack.pop();
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) node;
                if (element.hasAttribute("id")) {
                    String id = element.getAttribute("id");
                    if (id != null) {
                        String trimmed = id.trim();
                        if (!trimmed.isEmpty()) {
                            if (byId.containsKey(trimmed)) {
                                LOG.warn("Duplicate id '{}' encountered; last one wins.", trimmed);
                            }
                            byId.put(trimmed, element);
                        }
                    }
                }
            }

            NodeList children = node.getChildNodes();
            for (int i = children.getLength() - 1; i >= 0; i--) {
                stack.push(children.item(i));
            }
        }

        LOG.info("Loaded SVG: {} (ids indexed: {})", svgFile, Integer.valueOf(byId.size()));
        return new SvgDocument(document, byId);
    }
}
