package sk.arsi.corset.export;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Helper class for text-based replacement of d attributes in SVG path elements.
 * Preserves original file formatting, whitespace, attribute order, and metadata.
 */
public final class SvgTextDReplacer {

    private SvgTextDReplacer() {
        // utility class
    }

    /**
     * Replace the d attribute for a single path element by id.
     * 
     * @param svgText Original SVG text
     * @param id Path element id
     * @param newD New d attribute value
     * @return Modified SVG text with d attribute replaced
     * @throws IllegalArgumentException if path with id not found or if d attribute missing
     */
    public static String replacePathDById(String svgText, String id, String newD) {
        if (svgText == null || id == null || newD == null) {
            throw new IllegalArgumentException("svgText, id, and newD must not be null");
        }

        // Pattern to match the entire <path> start tag containing the given id
        // Uses DOTALL to match across newlines
        // Matches: <path ...any attributes... > where one attribute is id="ID"
        String idPattern = Pattern.quote(id);
        Pattern pathPattern = Pattern.compile(
            "<path\\s+[^>]*?id\\s*=\\s*\"" + idPattern + "\"[^>]*?>",
            Pattern.DOTALL
        );

        Matcher matcher = pathPattern.matcher(svgText);
        if (!matcher.find()) {
            throw new IllegalArgumentException("Path element with id=\"" + id + "\" not found in SVG text");
        }

        // Extract the full matched start tag
        String fullMatch = matcher.group(0);
        int matchStart = matcher.start();
        int matchEnd = matcher.end();

        // Check if d attribute exists in the matched tag
        Pattern dPattern = Pattern.compile("\\s+d\\s*=\\s*\"([^\"]*)\"", Pattern.DOTALL);
        Matcher dMatcher = dPattern.matcher(fullMatch);
        
        if (!dMatcher.find()) {
            throw new IllegalArgumentException("Path element with id=\"" + id + "\" has no d attribute");
        }

        // Replace the d attribute value in the matched tag
        String modifiedTag = dMatcher.replaceFirst(" d=\"" + Matcher.quoteReplacement(newD) + "\"");

        // Replace the original tag with the modified tag
        return svgText.substring(0, matchStart) + modifiedTag + svgText.substring(matchEnd);
    }

    /**
     * Replace d attributes for multiple path elements.
     * 
     * @param svgText Original SVG text
     * @param newDById Map from path id to new d attribute value
     * @return Modified SVG text with all d attributes replaced
     * @throws IllegalArgumentException if any path id not found or missing d attribute
     */
    public static String replaceMany(String svgText, Map<String, String> newDById) {
        if (svgText == null || newDById == null) {
            throw new IllegalArgumentException("svgText and newDById must not be null");
        }

        String result = svgText;
        for (Map.Entry<String, String> entry : newDById.entrySet()) {
            result = replacePathDById(result, entry.getKey(), entry.getValue());
        }
        return result;
    }
}
