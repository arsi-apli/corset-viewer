package sk.arsi.corset.allowance;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for OutputFileNamer.
 */
public class OutputFileNamerTest {

    private final OutputFileNamer namer = new OutputFileNamer();

    @Test
    public void testSimpleFilename() {
        Path input = Paths.get("/tmp/pattern.svg");
        Path output = namer.generateOutputPath(input);
        assertEquals("pattern_corset_viewer_allowances.svg", output.getFileName().toString());
    }

    @Test
    public void testFilenameWithCorsetViewer() {
        Path input = Paths.get("/tmp/pattern_corset_viewer.svg");
        Path output = namer.generateOutputPath(input);
        assertEquals("pattern_corset_viewer_allowances.svg", output.getFileName().toString());
    }

    @Test
    public void testFilenameWithCorsetViewerInMiddle() {
        Path input = Paths.get("/tmp/pattern_corset_viewer_fixed.svg");
        Path output = namer.generateOutputPath(input);
        assertEquals("pattern_corset_viewer_fixed_allowances.svg", output.getFileName().toString());
    }

    @Test
    public void testParentDirectoryPreserved() {
        Path input = Paths.get("/some/path/pattern.svg");
        Path output = namer.generateOutputPath(input);
        assertEquals(Paths.get("/some/path/pattern_corset_viewer_allowances.svg"), output);
    }

    @Test
    public void testNullInputThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            namer.generateOutputPath(null);
        });
    }

    @Test
    public void testNonSvgFileThrowsException() {
        Path input = Paths.get("/tmp/pattern.txt");
        assertThrows(IllegalArgumentException.class, () -> {
            namer.generateOutputPath(input);
        });
    }
}
