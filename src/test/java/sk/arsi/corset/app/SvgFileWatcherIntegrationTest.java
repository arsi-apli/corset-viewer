package sk.arsi.corset.app;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test simulating actual SVG file modification scenarios.
 */
class SvgFileWatcherIntegrationTest {

    @TempDir
    Path tempDir;

    /**
     * Simulates an Inkscape-style save where the file is written multiple times rapidly.
     */
    @Test
    void testInkscapeStyleMultiWrite() throws IOException, InterruptedException {
        Path svgFile = tempDir.resolve("inkscape-test.svg");
        String initialContent = "<?xml version=\"1.0\"?>\n<svg></svg>";
        Files.writeString(svgFile, initialContent);

        CountDownLatch firstReloadLatch = new CountDownLatch(1);
        AtomicInteger reloadCount = new AtomicInteger(0);

        SvgFileWatcher watcher = new SvgFileWatcher(svgFile, () -> {
            reloadCount.incrementAndGet();
            firstReloadLatch.countDown();
        });

        watcher.start();
        
        // Wait for watcher to initialize
        Thread.sleep(200);

        // Simulate Inkscape save: multiple rapid writes
        // Inkscape typically writes the file several times in quick succession
        for (int i = 0; i < 4; i++) {
            String content = "<?xml version=\"1.0\"?>\n<svg><rect id=\"r" + i + "\"/></svg>";
            Files.writeString(svgFile, content);
            Thread.sleep(10); // Very short delay between writes
        }

        // Wait for debounced reload
        boolean reloaded = firstReloadLatch.await(3, TimeUnit.SECONDS);
        
        assertTrue(reloaded, "Should trigger reload after rapid writes");
        
        // Wait a bit more to ensure no extra reloads
        Thread.sleep(1500);
        
        int finalCount = reloadCount.get();
        assertTrue(finalCount >= 1, "Should have at least one reload");
        assertTrue(finalCount <= 2, "Debouncing should limit reloads (got " + finalCount + ")");
        
        watcher.stop();
    }

    /**
     * Tests that the watcher handles file replacement (delete + create pattern).
     * Some editors save by creating a temp file and renaming it.
     */
    @Test
    void testFileCreateEventAfterInitialLoad() throws IOException, InterruptedException {
        Path svgFile = tempDir.resolve("create-test.svg");
        Files.writeString(svgFile, "<?xml version=\"1.0\"?>\n<svg></svg>");

        CountDownLatch reloadLatch = new CountDownLatch(1);
        AtomicInteger reloadCount = new AtomicInteger(0);

        SvgFileWatcher watcher = new SvgFileWatcher(svgFile, () -> {
            reloadCount.incrementAndGet();
            reloadLatch.countDown();
        });

        watcher.start();
        Thread.sleep(200);

        // Delete the file
        Files.delete(svgFile);
        Thread.sleep(100);

        // Recreate it with new content (simulates atomic save via temp file)
        Files.writeString(svgFile, "<?xml version=\"1.0\"?>\n<svg><circle/></svg>");

        // Wait for reload
        boolean reloaded = reloadLatch.await(3, TimeUnit.SECONDS);
        
        assertTrue(reloaded, "Should reload after file recreation");
        assertTrue(reloadCount.get() >= 1, "Should have reloaded");
        
        watcher.stop();
    }

    /**
     * Verifies that only the target file triggers reload, not other files in the same directory.
     */
    @Test
    void testSelectiveWatchingOfTargetFile() throws IOException, InterruptedException {
        Path targetSvg = tempDir.resolve("target.svg");
        Path otherSvg = tempDir.resolve("other.svg");
        
        Files.writeString(targetSvg, "<?xml version=\"1.0\"?>\n<svg></svg>");
        Files.writeString(otherSvg, "<?xml version=\"1.0\"?>\n<svg></svg>");

        CountDownLatch reloadLatch = new CountDownLatch(1);
        AtomicInteger reloadCount = new AtomicInteger(0);

        SvgFileWatcher watcher = new SvgFileWatcher(targetSvg, () -> {
            reloadCount.incrementAndGet();
            reloadLatch.countDown();
        });

        watcher.start();
        Thread.sleep(200);

        // Modify the OTHER file - should NOT trigger reload
        Files.writeString(otherSvg, "<?xml version=\"1.0\"?>\n<svg><rect/></svg>");
        Thread.sleep(1500); // Wait more than debounce time

        assertEquals(0, reloadCount.get(), "Should not reload when other file is modified");

        // Now modify the TARGET file - should trigger reload
        Files.writeString(targetSvg, "<?xml version=\"1.0\"?>\n<svg><rect/></svg>");

        boolean reloaded = reloadLatch.await(3, TimeUnit.SECONDS);
        assertTrue(reloaded, "Should reload when target file is modified");
        assertEquals(1, reloadCount.get(), "Should reload exactly once");
        
        watcher.stop();
    }

    /**
     * Simulates continuous editing session with multiple save cycles.
     */
    @Test
    void testMultipleSaveCycles() throws IOException, InterruptedException {
        Path svgFile = tempDir.resolve("edit-session.svg");
        Files.writeString(svgFile, "<?xml version=\"1.0\"?>\n<svg></svg>");

        CountDownLatch reloadLatch = new CountDownLatch(3);
        AtomicInteger reloadCount = new AtomicInteger(0);

        SvgFileWatcher watcher = new SvgFileWatcher(svgFile, () -> {
            reloadCount.incrementAndGet();
            reloadLatch.countDown();
        });

        watcher.start();
        Thread.sleep(200);

        // Simulate 3 distinct save events with enough time between them
        for (int cycle = 1; cycle <= 3; cycle++) {
            String content = "<?xml version=\"1.0\"?>\n<svg><rect id=\"cycle" + cycle + "\"/></svg>";
            Files.writeString(svgFile, content);
            
            // Wait enough time for debounce to complete
            Thread.sleep(1200);
        }

        boolean allReloaded = reloadLatch.await(2, TimeUnit.SECONDS);
        
        assertTrue(allReloaded, "Should reload for each distinct save cycle");
        assertTrue(reloadCount.get() >= 3, "Should have reloaded 3 times for 3 cycles");
        
        watcher.stop();
    }
}
