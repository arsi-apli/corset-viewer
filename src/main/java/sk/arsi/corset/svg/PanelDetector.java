package sk.arsi.corset.svg;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Heuristic detector for max panel letter from existing SVG IDs.
 */
public final class PanelDetector {
    
    // Pattern to match panel IDs like "A_TOP", "B_WAIST", "CD_UP", etc.
    private static final Pattern PANEL_PATTERN = Pattern.compile("^([A-Z])(_TOP|_WAIST|_BOTTOM|[A-Z])");
    
    private PanelDetector() {
    }
    
    /**
     * Detect max panel letter from existing IDs in the SVG.
     * Scans for IDs matching panel patterns (A_TOP, B_WAIST, etc.) and returns the highest letter found.
     * 
     * @param svgDocument the SVG document to scan
     * @return the highest panel letter found (A-Z), or 'F' as default if no panels detected
     */
    public static char detectMaxPanel(SvgDocument svgDocument) {
        Set<Character> foundPanels = new HashSet<>();
        
        // Scan all element IDs
        for (String id : svgDocument.getElementsById().keySet()) {
            Matcher matcher = PANEL_PATTERN.matcher(id);
            if (matcher.find()) {
                char panelLetter = matcher.group(1).charAt(0);
                foundPanels.add(panelLetter);
            }
        }
        
        // Return highest letter found, or 'F' as default
        char maxPanel = 'F';
        for (char panel : foundPanels) {
            if (panel > maxPanel) {
                maxPanel = panel;
            }
        }
        
        return maxPanel;
    }
}
