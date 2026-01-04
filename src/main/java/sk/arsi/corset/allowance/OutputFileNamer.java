package sk.arsi.corset.allowance;

import java.nio.file.Path;

/**
 * Utility for generating output filenames for SVG files with allowances.
 */
public final class OutputFileNamer {

    private static final String CORSET_VIEWER_MARKER = "corset_viewer";
    private static final String ALLOWANCES_SUFFIX = "_allowances";
    private static final String SVG_EXTENSION = ".svg";

    /**
     * Generate output filename for SVG with allowances.
     * 
     * Rules:
     * - If base filename contains "corset_viewer" anywhere, append "_allowances"
     * - Otherwise, append "_corset_viewer_allowances"
     * - Always keep .svg extension
     * 
     * Examples:
     * - pattern.svg -> pattern_corset_viewer_allowances.svg
     * - pattern_corset_viewer.svg -> pattern_corset_viewer_allowances.svg
     * - pattern_corset_viewer_fixed.svg -> pattern_corset_viewer_fixed_allowances.svg
     */
    public Path generateOutputPath(Path inputPath) {
        if (inputPath == null) {
            throw new IllegalArgumentException("Input path cannot be null");
        }

        String filename = inputPath.getFileName().toString();
        if (!filename.toLowerCase().endsWith(SVG_EXTENSION)) {
            throw new IllegalArgumentException("Input file must be an SVG file: " + filename);
        }

        // Remove .svg extension
        String baseName = filename.substring(0, filename.length() - SVG_EXTENSION.length());

        // Generate new filename
        String newBaseName;
        if (baseName.contains(CORSET_VIEWER_MARKER)) {
            newBaseName = baseName + ALLOWANCES_SUFFIX;
        } else {
            newBaseName = baseName + "_" + CORSET_VIEWER_MARKER + ALLOWANCES_SUFFIX;
        }

        String newFilename = newBaseName + SVG_EXTENSION;
        return inputPath.getParent().resolve(newFilename);
    }
}
