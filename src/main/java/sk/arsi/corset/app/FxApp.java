package sk.arsi.corset.app;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import sk.arsi.corset.io.SvgFileWatcher;
import sk.arsi.corset.io.SvgPanelLoader;
import sk.arsi.corset.model.PanelCurves;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.prefs.Preferences;

public final class FxApp extends Application {

    private static final String PREF_NODE = "sk.arsi.corset-viewer";
    private static final String PREF_LAST_DIR = "lastSvgDir";

    private Canvas2DView view2d;
    private Pseudo3DView viewPseudo3d;
    private MeasurementsView viewMeasurements;

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
        // --- Resolve SVG path (arg or file chooser) ---
        svgPath = resolveSvgPath(stage);
        if (svgPath == null) {
            // User cancelled
            Platform.exit();
            return;
        }

        List<PanelCurves> panels = panelLoader.loadPanelsWithRetry(svgPath, 3, 250);

        // --- Measurements ---
        viewMeasurements = new MeasurementsView();
        viewMeasurements.setPanels(panels);

        // --- 2D ---
        view2d = new Canvas2DView();
        view2d.setPanels(panels);
        view2d.setSeamMeasurements(viewMeasurements);

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

        Tab tabMeasurements = new Tab("Measurements");
        tabMeasurements.setClosable(false);
        tabMeasurements.setContent(viewMeasurements.getNode());

        TabPane tabs = new TabPane(tab2d, tabPseudo3d, tabMeasurements);

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

    private Path resolveSvgPath(Stage stage) {
        List<String> args = getParameters().getRaw();

        if (args.size() == 1) {
            return Path.of(args.get(0));
        }

        if (!args.isEmpty()) {
            // Keep strictness for unexpected args count
            throw new IllegalArgumentException("Expected 0 or 1 arg: path to SVG");
        }

        // No args -> file chooser
        Preferences prefs = Preferences.userRoot().node(PREF_NODE);
        String lastDir = prefs.get(PREF_LAST_DIR, null);

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Open corset SVG");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("SVG files (*.svg)", "*.svg")
        );

        // initial dir if available
        if (lastDir != null) {
            try {
                Path p = Path.of(lastDir);
                if (Files.isDirectory(p)) {
                    chooser.setInitialDirectory(p.toFile());
                }
            } catch (Exception ignored) {
                // ignore invalid preference value
            }
        }

        File chosen = chooser.showOpenDialog(stage);
        if (chosen == null) {
            return null;
        }

        // store parent dir
        File parent = chosen.getParentFile();
        if (parent != null) {
            prefs.put(PREF_LAST_DIR, parent.getAbsolutePath());
        }

        return chosen.toPath();
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
        reloadExec.submit(() -> {
            try {
                List<PanelCurves> panels = panelLoader.loadPanelsWithRetry(svgPath, 3, 250);
                Platform.runLater(() -> {
                    if (viewMeasurements != null) {
                        viewMeasurements.setPanels(panels);
                    }
                    if (view2d != null) {
                        view2d.setPanels(panels);
                    }
                    if (viewPseudo3d != null) {
                        viewPseudo3d.setPanels(panels);
                    }
                });
            } catch (Exception ignored) {
                // ignore reload failures, user can fix svg and save again
            }
        });
    }

    private void stopResources() {
        stopWatching();
        reloadExec.shutdownNow();
    }
}
