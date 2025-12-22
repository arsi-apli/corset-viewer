package sk.arsi.corset.app;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import sk.arsi.corset.io.SvgFileWatcher;
import sk.arsi.corset.io.SvgPanelLoader;
import sk.arsi.corset.model.PanelCurves;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class FxApp extends Application {

    private Canvas2DView view2d;
    private Pseudo3DView viewPseudo3d;

    private Path svgPath;

    private SvgFileWatcher watcher;

    private final ExecutorService reloadExec = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "svg-reload-worker");
        t.setDaemon(true);
        return t;
    });

    private final SvgPanelLoader panelLoader = new SvgPanelLoader(0.2, 0.5);

    @Override
    public void start(Stage stage) throws Exception {
        List<String> args = getParameters().getRaw();
        if (args.size() != 1) {
            throw new IllegalArgumentException("Expected 1 arg: path to SVG");
        }

        svgPath = Path.of(args.get(0));

        List<PanelCurves> panels = panelLoader.loadPanelsWithRetry(svgPath, 3, 250);

        // --- 2D ---
        view2d = new Canvas2DView();
        view2d.setPanels(panels);

        // --- Pseudo 3D ---
        viewPseudo3d = new Pseudo3DView();
        viewPseudo3d.setPanels(panels);

        // --- Tabs ---
        Tab tab2d = new Tab("2D");
        tab2d.setClosable(false);
        tab2d.setContent(view2d.getNode());

        Tab tabPseudo3d = new Tab("Pseudo 3D");
        tabPseudo3d.setClosable(false);
        tabPseudo3d.setContent(viewPseudo3d.getNode());

        TabPane tabs = new TabPane(tab2d, tabPseudo3d);

        BorderPane root = new BorderPane();
        root.setCenter(tabs);

        Scene scene = new Scene(root, 1200, 700);
        stage.setTitle("Corset Viewer");
        stage.setScene(scene);
        stage.show();

        startWatching(svgPath);

        // stop watcher cleanly on window close
        stage.setOnCloseRequest(e -> stopResources());
    }

    private void startWatching(Path path) {
        stopWatching();

        watcher = new SvgFileWatcher(
                path,
                800, // debounce for Inkscape multi-write
                this::reloadSvgAsync
        );

        try {
            watcher.start();
        } catch (Exception ignored) {
            // if watcher can't start, app still works without auto-reload
        }
    }

    private void stopWatching() {
        if (watcher != null) {
            watcher.stop();
            watcher = null;
        }
    }

    private void reloadSvgAsync() {
        final Path path = svgPath;
        if (path == null) {
            return;
        }

        reloadExec.submit(() -> {
            try {
                List<PanelCurves> panels = panelLoader.loadPanelsWithRetry(path, 3, 250);

                Platform.runLater(() -> {
                    if (view2d != null) {
                        view2d.setPanels(panels);
                    }
                    if (viewPseudo3d != null) {
                        viewPseudo3d.setPanels(panels);
                    }
                });
            } catch (Exception ignored) {
                // ignore transient parse failures during save
            }
        });
    }

    private void stopResources() {
        stopWatching();
        reloadExec.shutdownNow();
    }

    @Override
    public void stop() {
        stopResources();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
