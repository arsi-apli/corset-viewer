package sk.arsi.corset.wizard;

import sk.arsi.corset.svg.SvgDocument;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Text-based SVG editor that preserves Inkscape formatting.
 */
public final class SvgTextEditor {
    
    /**
     * Update the SVG file with new ID assignments.
     * 
     * @param sourcePath Original SVG file path
     * @param targetPath Target SVG file path (can be same as source)
     * @param session Wizard session with assignments
     * @throws IOException if file operations fail
     */
    public void saveWithAssignments(Path sourcePath, Path targetPath, IdWizardSession session) throws IOException {
        String content = Files.readString(sourcePath, StandardCharsets.UTF_8);
        
        // Get all candidates that need ID updates
        List<SvgPathCandidate> toUpdate = new ArrayList<>();
        for (SvgPathCandidate candidate : session.getCandidates()) {
            if (candidate.getAssignedRequired() != null && !candidate.isOriginallyRequiredId()) {
                toUpdate.add(candidate);
            }
        }
        
        // Update each candidate
        for (SvgPathCandidate candidate : toUpdate) {
            String newId = candidate.getAssignedRequired().svgId();
            content = updatePathId(content, candidate, newId);
        }
        
        // Write to target file
        Files.writeString(targetPath, content, StandardCharsets.UTF_8);
    }

    /**
     * Update a single path element's ID attribute.
     */
    private String updatePathId(String content, SvgPathCandidate candidate, String newId) {
        String dAttr = candidate.getDAttribute();
        String originalId = candidate.getOriginalId();
        
        // Escape special regex characters in d attribute
        String escapedD = Pattern.quote(dAttr);
        
        // Build pattern to find the path element
        // We need to be careful to match the exact path
        String pattern;
        
        if (originalId != null && !originalId.isEmpty()) {
            // Path has an existing id attribute - replace it
            // Match: <path ... id="originalId" ... d="dAttr" ... >
            // OR:    <path ... d="dAttr" ... id="originalId" ... >
            
            String escapedOriginalId = Pattern.quote(originalId);
            
            // Try to find and replace the id attribute value
            // Pattern: id="originalId" where the path also has d="dAttr"
            Pattern p = Pattern.compile(
                "(<path[^>]*?\\bd=\"" + escapedD + "\"[^>]*?\\bid=\\\")(" + escapedOriginalId + ")(\\\"[^>]*?>)" +
                "|(<path[^>]*?\\bid=\")(" + escapedOriginalId + ")(\\\"[^>]*?\\bd=\"" + escapedD + "\"[^>]*?>)",
                Pattern.DOTALL
            );
            
            Matcher m = p.matcher(content);
            if (m.find()) {
                if (m.group(1) != null) {
                    // First alternative matched (d before id)
                    return m.replaceFirst(m.group(1) + newId + m.group(3));
                } else {
                    // Second alternative matched (id before d)
                    return m.replaceFirst(m.group(4) + newId + m.group(6));
                }
            }
            
            // Fallback: couldn't find the exact match
            throw new IllegalStateException("Could not uniquely identify path element with id=" + originalId + " and d=" + dAttr.substring(0, Math.min(50, dAttr.length())) + "...");
        } else {
            // Path has no id attribute - insert one
            // Match: <path ... d="dAttr" ... >
            // Insert id attribute after <path
            
            Pattern p = Pattern.compile(
                "(<path)(\\s[^>]*?\\bd=\"" + escapedD + "\"[^>]*?>)",
                Pattern.DOTALL
            );
            
            Matcher m = p.matcher(content);
            if (m.find()) {
                return m.replaceFirst(m.group(1) + " id=\"" + newId + "\"" + m.group(2));
            }
            
            // Fallback: couldn't find the path
            throw new IllegalStateException("Could not find path element with d=" + dAttr.substring(0, Math.min(50, dAttr.length())) + "...");
        }
    }
    
    /**
     * Write or update the max panel metadata attribute on the SVG root element.
     * 
     * @param svgPath path to the SVG file
     * @param maxPanel the max panel letter (A-Z)
     * @throws IOException if file operations fail
     */
    public void writeMaxPanelMetadata(Path svgPath, char maxPanel) throws IOException {
        String content = Files.readString(svgPath, StandardCharsets.UTF_8);
        
        // Find the opening <svg> tag
        Pattern svgPattern = Pattern.compile("(<svg[^>]*?)(/?>)", Pattern.DOTALL);
        Matcher matcher = svgPattern.matcher(content);
        
        if (!matcher.find()) {
            throw new IllegalStateException("Could not find <svg> root element");
        }
        
        String svgOpenTag = matcher.group(1);
        String svgClose = matcher.group(2);
        
        // Check if attribute already exists
        String attrName = SvgDocument.ATTR_MAX_PANEL;
        Pattern attrPattern = Pattern.compile("\\b" + Pattern.quote(attrName) + "\\s*=\\s*\"[^\"]*\"");
        Matcher attrMatcher = attrPattern.matcher(svgOpenTag);
        
        String updatedSvgOpenTag;
        if (attrMatcher.find()) {
            // Update existing attribute
            updatedSvgOpenTag = attrMatcher.replaceFirst(attrName + "=\"" + maxPanel + "\"");
        } else {
            // Insert new attribute before the closing > or />
            updatedSvgOpenTag = svgOpenTag + "\n   " + attrName + "=\"" + maxPanel + "\"";
        }
        
        String updatedContent = matcher.replaceFirst(updatedSvgOpenTag + svgClose);
        Files.writeString(svgPath, updatedContent, StandardCharsets.UTF_8);
    }
}
