package sk.arsi.corset.app;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
 * Tests for SvgFileWatcher, focusing on debounce behavior and lifecycle.
 */
class SvgFileWatcherTest {

    @TempDir
    Path tempDir;

    private SvgFileWatcher watcher;

    @AfterEach
    void cleanup() {
        if (watcher != null) {
            watcher.stop();
            watcher = null;
        }
    }

    @Test
    void testBasicFileWatch() throws IOException, InterruptedException {
        // Create a test SVG file
        Path svgFile = tempDir.resolve("test.svg");
        Files.writeString(svgFile, "<svg></svg>");

        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger callCount = new AtomicInteger(0);

        watcher = new SvgFileWatcher(svgFile, () -> {
            callCount.incrementAndGet();
            latch.countDown();
        });

        watcher.start();

        // Wait a bit for watcher to initialize
        Thread.sleep(100);

        // Modify the file
        Files.writeString(svgFile, "<svg><rect/></svg>");

        // Wait for callback (with debounce delay)
        boolean triggered = latch.await(3, TimeUnit.SECONDS);

        assertTrue(triggered, "Callback should have been triggered");
        assertTrue(callCount.get() >= 1, "Callback should have been called at least once");
    }

    @Test
    void testDebounceMultipleWrites() throws IOException, InterruptedException {
        Path svgFile = tempDir.resolve("test-debounce.svg");
        Files.writeString(svgFile, "<svg></svg>");

        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger callCount = new AtomicInteger(0);

        watcher = new SvgFileWatcher(svgFile, () -> {
            callCount.incrementAndGet();
            latch.countDown();
        });

        watcher.start();
        Thread.sleep(100);

        // Write multiple times rapidly (simulating editor saves)
        for (int i = 0; i < 5; i++) {
            Files.writeString(svgFile, "<svg><rect id=\"" + i + "\"/></svg>");
            Thread.sleep(50); // Small delay between writes
        }

        // Wait for callback
        boolean triggered = latch.await(3, TimeUnit.SECONDS);

        assertTrue(triggered, "Callback should have been triggered");
        
        // Due to debouncing, we should have fewer callbacks than writes
        // Give it a bit more time to ensure no extra callbacks fire
        Thread.sleep(1500);
        
        // Debouncing should reduce the number of actual reload calls
        int finalCount = callCount.get();
        assertTrue(finalCount >= 1, "Should have at least one callback");
        assertTrue(finalCount <= 3, "Debouncing should reduce callbacks (got " + finalCount + ")");
    }

    @Test
    void testStopWatcher() throws IOException, InterruptedException {
        Path svgFile = tempDir.resolve("test-stop.svg");
        Files.writeString(svgFile, "<svg></svg>");

        AtomicInteger callCount = new AtomicInteger(0);

        watcher = new SvgFileWatcher(svgFile, callCount::incrementAndGet);
        watcher.start();
        Thread.sleep(100);

        // Stop the watcher
        watcher.stop();
        
        // Wait for cleanup
        Thread.sleep(200);

        // Modify file - should not trigger callback
        Files.writeString(svgFile, "<svg><rect/></svg>");

        // Wait to ensure no callback
        Thread.sleep(1500);

        assertEquals(0, callCount.get(), "No callbacks should occur after stop");
    }

    @Test
    void testIdempotentStart() throws IOException, InterruptedException {
        Path svgFile = tempDir.resolve("test-idempotent.svg");
        Files.writeString(svgFile, "<svg></svg>");

        watcher = new SvgFileWatcher(svgFile, () -> {});
        
        // Start multiple times
        watcher.start();
        watcher.start();
        watcher.start();

        // Should not throw or cause issues
        Thread.sleep(100);
        
        watcher.stop();
    }

    @Test
    void testIdempotentStop() throws IOException {
        Path svgFile = tempDir.resolve("test-idempotent-stop.svg");
        Files.writeString(svgFile, "<svg></svg>");

        watcher = new SvgFileWatcher(svgFile, () -> {});
        
        // Stop without starting
        watcher.stop();
        watcher.stop();
        watcher.stop();

        // Should not throw
    }

    @Test
    void testNullFilePathThrows() {
        assertThrows(IllegalArgumentException.class, () -> {
            new SvgFileWatcher(null, () -> {});
        });
    }

    @Test
    void testNullCallbackThrows() throws IOException {
        Path svgFile = tempDir.resolve("test.svg");
        Files.writeString(svgFile, "<svg></svg>");

        assertThrows(IllegalArgumentException.class, () -> {
            new SvgFileWatcher(svgFile, null);
        });
    }
}
