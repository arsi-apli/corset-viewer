package sk.arsi.corset.io;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.nio.file.StandardWatchEventKinds.*;

public final class SvgFileWatcher implements Closeable {

    private final Path svgPath;
    private final long settleDelayMs;
    private final Runnable onStableChange;

    private WatchService watchService;
    private Thread watchThread;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "svg-watcher-debounce");
        t.setDaemon(true);
        return t;
    });

    private final AtomicBoolean running = new AtomicBoolean(false);
    private ScheduledFuture<?> pending;

    public SvgFileWatcher(Path svgPath, long settleDelayMs, Runnable onStableChange) {
        this.svgPath = svgPath.toAbsolutePath();
        this.settleDelayMs = settleDelayMs;
        this.onStableChange = onStableChange;
    }

    public void start() throws IOException {
        if (running.getAndSet(true)) {
            return;
        }

        Path dir = svgPath.getParent();
        if (dir == null) {
            throw new IOException("SVG path has no parent directory: " + svgPath);
        }

        watchService = FileSystems.getDefault().newWatchService();
        dir.register(watchService, ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE);

        watchThread = new Thread(this::runLoop, "svg-watcher");
        watchThread.setDaemon(true);
        watchThread.start();
    }

    private void runLoop() {
        Path watchedFileName = svgPath.getFileName();

        while (running.get()) {
            WatchKey key;
            try {
                key = watchService.take();
            } catch (InterruptedException | ClosedWatchServiceException e) {
                return;
            }

            boolean relevant = false;
            for (WatchEvent<?> ev : key.pollEvents()) {
                Object ctx = ev.context();
                if (ctx instanceof Path p && p.equals(watchedFileName)) {
                    relevant = true;
                }
            }

            if (relevant) {
                scheduleDebouncedReload();
            }

            if (!key.reset()) {
                break;
            }
        }
    }

    private synchronized void scheduleDebouncedReload() {
        if (!running.get()) {
            return;
        }

        if (pending != null) {
            pending.cancel(false);
        }
        pending = scheduler.schedule(onStableChange, settleDelayMs, TimeUnit.MILLISECONDS);
    }

    public void stop() {
        if (!running.getAndSet(false)) {
            return;
        }

        synchronized (this) {
            if (pending != null) {
                pending.cancel(false);
            }
            pending = null;
        }

        try {
            if (watchService != null) {
                watchService.close();
            }
        } catch (IOException ignored) {
        }

        scheduler.shutdownNow();
    }

    @Override
    public void close() {
        stop();
    }
}
