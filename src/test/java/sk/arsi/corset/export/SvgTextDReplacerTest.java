package sk.arsi.corset.export;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SvgTextDReplacer.
 */
public class SvgTextDReplacerTest {

    @Test
    public void testReplacePathDById_IdBeforeD() {
        String svg = "<svg><path id=\"test\" d=\"M 0 0 L 10 10\" stroke=\"black\"/></svg>";
        String result = SvgTextDReplacer.replacePathDById(svg, "test", "M 5 5 L 15 15");
        
        assertTrue(result.contains("d=\"M 5 5 L 15 15\""));
        assertFalse(result.contains("M 0 0 L 10 10"));
        assertTrue(result.contains("id=\"test\""));
        assertTrue(result.contains("stroke=\"black\""));
    }

    @Test
    public void testReplacePathDById_DBeforeId() {
        String svg = "<svg><path d=\"M 0 0 L 10 10\" id=\"test\" stroke=\"black\"/></svg>";
        String result = SvgTextDReplacer.replacePathDById(svg, "test", "M 5 5 L 15 15");
        
        assertTrue(result.contains("d=\"M 5 5 L 15 15\""));
        assertFalse(result.contains("M 0 0 L 10 10"));
        assertTrue(result.contains("id=\"test\""));
        assertTrue(result.contains("stroke=\"black\""));
    }

    @Test
    public void testReplacePathDById_MultilineAttributes() {
        String svg = "<svg>\n" +
                     "  <path\n" +
                     "    id=\"test\"\n" +
                     "    d=\"M 0 0 L 10 10\"\n" +
                     "    stroke=\"black\"\n" +
                     "    fill=\"none\"/>\n" +
                     "</svg>";
        String result = SvgTextDReplacer.replacePathDById(svg, "test", "M 5 5 L 15 15");
        
        assertTrue(result.contains("d=\"M 5 5 L 15 15\""));
        assertFalse(result.contains("M 0 0 L 10 10"));
        assertTrue(result.contains("id=\"test\""));
        assertTrue(result.contains("stroke=\"black\""));
    }

    @Test
    public void testReplacePathDById_MissingId() {
        String svg = "<svg><path id=\"other\" d=\"M 0 0 L 10 10\"/></svg>";
        
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            SvgTextDReplacer.replacePathDById(svg, "missing", "M 5 5 L 15 15");
        });
        
        assertTrue(exception.getMessage().contains("missing"));
        assertTrue(exception.getMessage().contains("not found"));
    }

    @Test
    public void testReplacePathDById_MissingD() {
        String svg = "<svg><path id=\"test\" stroke=\"black\"/></svg>";
        
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            SvgTextDReplacer.replacePathDById(svg, "test", "M 5 5 L 15 15");
        });
        
        assertTrue(exception.getMessage().contains("test"));
        assertTrue(exception.getMessage().contains("no d attribute"));
    }

    @Test
    public void testReplacePathDById_PreservesOtherElements() {
        String svg = "<svg>" +
                     "<path id=\"keep\" d=\"M 0 0 L 5 5\"/>" +
                     "<path id=\"test\" d=\"M 0 0 L 10 10\"/>" +
                     "<circle id=\"circle\" cx=\"10\" cy=\"10\" r=\"5\"/>" +
                     "</svg>";
        String result = SvgTextDReplacer.replacePathDById(svg, "test", "M 5 5 L 15 15");
        
        // Modified path
        assertTrue(result.contains("id=\"test\""));
        assertTrue(result.contains("d=\"M 5 5 L 15 15\""));
        
        // Unchanged elements
        assertTrue(result.contains("id=\"keep\""));
        assertTrue(result.contains("M 0 0 L 5 5"));
        assertTrue(result.contains("id=\"circle\""));
        assertTrue(result.contains("cx=\"10\""));
    }

    @Test
    public void testReplacePathDById_SpecialCharactersInD() {
        String svg = "<svg><path id=\"test\" d=\"M 0 0 C 10 10 20 20 30 30\"/></svg>";
        String newD = "M 0,0 C 10.5,10.5 20.5,20.5 30.5,30.5 Z";
        String result = SvgTextDReplacer.replacePathDById(svg, "test", newD);
        
        assertTrue(result.contains(newD));
        assertFalse(result.contains("M 0 0 C 10 10 20 20 30 30"));
    }

    @Test
    public void testReplaceMany_MultiplePaths() {
        String svg = "<svg>" +
                     "<path id=\"path1\" d=\"M 0 0 L 10 10\"/>" +
                     "<path id=\"path2\" d=\"M 20 20 L 30 30\"/>" +
                     "<path id=\"path3\" d=\"M 40 40 L 50 50\"/>" +
                     "</svg>";
        
        Map<String, String> replacements = new HashMap<>();
        replacements.put("path1", "M 1 1 L 11 11");
        replacements.put("path3", "M 41 41 L 51 51");
        
        String result = SvgTextDReplacer.replaceMany(svg, replacements);
        
        // Modified paths
        assertTrue(result.contains("id=\"path1\""));
        assertTrue(result.contains("d=\"M 1 1 L 11 11\""));
        assertTrue(result.contains("id=\"path3\""));
        assertTrue(result.contains("d=\"M 41 41 L 51 51\""));
        
        // Unchanged path
        assertTrue(result.contains("id=\"path2\""));
        assertTrue(result.contains("d=\"M 20 20 L 30 30\""));
    }

    @Test
    public void testReplaceMany_EmptyMap() {
        String svg = "<svg><path id=\"test\" d=\"M 0 0 L 10 10\"/></svg>";
        Map<String, String> replacements = new HashMap<>();
        
        String result = SvgTextDReplacer.replaceMany(svg, replacements);
        
        // Should be unchanged
        assertEquals(svg, result);
    }

    @Test
    public void testReplacePathDById_WithNamespaces() {
        String svg = "<svg xmlns=\"http://www.w3.org/2000/svg\">" +
                     "<path id=\"test\" d=\"M 0 0 L 10 10\" xmlns:inkscape=\"http://www.inkscape.org/namespaces/inkscape\"/>" +
                     "</svg>";
        String result = SvgTextDReplacer.replacePathDById(svg, "test", "M 5 5 L 15 15");
        
        assertTrue(result.contains("d=\"M 5 5 L 15 15\""));
        assertTrue(result.contains("xmlns=\"http://www.w3.org/2000/svg\""));
        assertTrue(result.contains("xmlns:inkscape="));
    }
}
