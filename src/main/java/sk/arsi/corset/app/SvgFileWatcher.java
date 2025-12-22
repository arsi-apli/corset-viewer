package sk.arsi.corset.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Watches a single SVG file for changes and triggers reload callbacks.
 * Uses Java NIO WatchService to monitor the parent directory and filters
 * events for the target file. Includes debouncing to handle multiple rapid
 * writes (common when saving from editors like Inkscape).
 */
public final class SvgFileWatcher {

    private static final Logger LOG = LoggerFactory.getLogger(SvgFileWatcher.class);
    
    private static final long DEBOUNCE_DELAY_MS = 800;
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 300;

    private final Path filePath;
    private final Runnable reloadCallback;
    
    private WatchService watchService;
    private Thread watchThread;
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> pendingReload;
    
    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * Creates a file watcher for the specified SVG file.
     * 
     * @param filePath The SVG file to watch
     * @param reloadCallback Callback to invoke when file changes (called on scheduler thread)
     */
    public SvgFileWatcher(Path filePath, Runnable reloadCallback) {
        if (filePath == null) {
            throw new IllegalArgumentException("filePath cannot be null");
        }
        if (reloadCallback == null) {
            throw new IllegalArgumentException("reloadCallback cannot be null");
        }
        this.filePath = filePath.toAbsolutePath().normalize();
        this.reloadCallback = reloadCallback;
    }

    /**
     * Starts watching the file. This method is idempotent - calling it multiple
     * times has no additional effect.
     */
    public void start() {
        if (!running.compareAndSet(false, true)) {
            LOG.debug("File watcher already running for {}", filePath);
            return;
        }

        try {
            Path parent = filePath.getParent();
            if (parent == null) {
                throw new IllegalStateException("Cannot watch file without parent directory: " + filePath);
            }
            
            watchService = FileSystems.getDefault().newWatchService();
            parent.register(watchService, 
                StandardWatchEventKinds.ENTRY_MODIFY, 
                StandardWatchEventKinds.ENTRY_CREATE);
            
            scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "SVG-Reload-Scheduler");
                t.setDaemon(true);
                return t;
            });
            
            watchThread = new Thread(this::watchLoop, "SVG-File-Watcher");
            watchThread.setDaemon(true);
            watchThread.start();
            
            LOG.info("Started watching SVG file: {}", filePath);
        } catch (IOException e) {
            running.set(false);
            LOG.error("Failed to start file watcher for {}", filePath, e);
            cleanup();
        }
    }

    /**
     * Stops watching the file and releases resources. This method is idempotent.
     */
    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        
        LOG.info("Stopping file watcher for {}", filePath);
        cleanup();
    }

    private void cleanup() {
        // Cancel any pending reload
        if (pendingReload != null && !pendingReload.isDone()) {
            pendingReload.cancel(false);
            pendingReload = null;
        }
        
        // Shutdown scheduler
        if (scheduler != null) {
            scheduler.shutdownNow();
            try {
                scheduler.awaitTermination(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            scheduler = null;
        }
        
        // Close watch service (this will unblock the watch thread)
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException e) {
                LOG.warn("Error closing watch service", e);
            }
            watchService = null;
        }
        
        // Wait for watch thread to finish
        if (watchThread != null && watchThread.isAlive()) {
            try {
                watchThread.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            watchThread = null;
        }
    }

    private void watchLoop() {
        String targetFileName = filePath.getFileName().toString();
        
        while (running.get()) {
            try {
                WatchKey key = watchService.take(); // Blocks until event or closed
                
                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();
                    
                    if (kind == StandardWatchEventKinds.OVERFLOW) {
                        LOG.warn("Watch event overflow - some events may have been lost");
                        continue;
                    }
                    
                    @SuppressWarnings("unchecked")
                    WatchEvent<Path> pathEvent = (WatchEvent<Path>) event;
                    Path changedFile = pathEvent.context();
                    
                    if (changedFile != null && changedFile.toString().equals(targetFileName)) {
                        LOG.debug("Detected change to {}: {}", targetFileName, kind);
                        scheduleReload();
                    }
                }
                
                boolean valid = key.reset();
                if (!valid) {
                    LOG.warn("Watch key no longer valid - stopping watcher");
                    running.set(false);
                    break;
                }
            } catch (ClosedWatchServiceException e) {
                // Expected when stop() is called
                LOG.debug("Watch service closed");
                break;
            } catch (InterruptedException e) {
                LOG.debug("Watch thread interrupted");
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                LOG.error("Unexpected error in watch loop", e);
                // Continue watching unless we're supposed to stop
            }
        }
        
        LOG.debug("Watch loop exiting");
    }

    private void scheduleReload() {
        // Cancel any pending reload
        if (pendingReload != null && !pendingReload.isDone()) {
            pendingReload.cancel(false);
        }
        
        // Schedule new reload with debounce delay
        pendingReload = scheduler.schedule(this::executeReloadWithRetry, DEBOUNCE_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    private void executeReloadWithRetry() {
        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                LOG.info("Reloading SVG file (attempt {}/{}): {}", attempt, MAX_RETRY_ATTEMPTS, filePath);
                reloadCallback.run();
                LOG.info("Successfully reloaded SVG file");
                return; // Success
            } catch (Exception e) {
                LOG.warn("Reload attempt {}/{} failed: {}", attempt, MAX_RETRY_ATTEMPTS, e.getMessage());
                
                if (attempt < MAX_RETRY_ATTEMPTS) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        LOG.warn("Retry delay interrupted");
                        return;
                    }
                } else {
                    LOG.error("Failed to reload SVG file after {} attempts", MAX_RETRY_ATTEMPTS, e);
                }
            }
        }
    }
}
