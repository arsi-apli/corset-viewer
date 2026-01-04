package sk.arsi.corset.app;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import sk.arsi.corset.io.SvgFileWatcher;
import sk.arsi.corset.io.SvgPanelLoader;
import sk.arsi.corset.model.PanelCurves;
import sk.arsi.corset.svg.PathSampler;
import sk.arsi.corset.svg.PatternContract;
import sk.arsi.corset.svg.PatternExtractor;
import sk.arsi.corset.svg.SvgDocument;
import sk.arsi.corset.svg.SvgLoader;
import sk.arsi.corset.wizard.IdAssignmentWizard;
import sk.arsi.corset.wizard.IdWizardSession;
import sk.arsi.corset.wizard.SvgTextEditor;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.prefs.Preferences;

public final class FxApp extends Application {

    private static final String PREF_NODE = "sk.arsi.corset-viewer";
    private static final String PREF_LAST_DIR = "lastSvgDir";
    
    /** Suffix for wizard-generated SVG files */
    public static final String CORSET_VIEWER_SUFFIX = "_corset_viewer.svg";

    private Canvas2DView view2d;
    private Pseudo3DView viewPseudo3d;
    private MeasurementsView viewMeasurements;

    private Path svgPath;
    private SvgDocument svgDocument;

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

        // Try to load panels, launch wizard if required IDs are missing
        List<PanelCurves> panels = loadPanelsOrLaunchWizard(stage, svgPath);
        if (panels == null) {
            // User cancelled wizard or error occurred
            Platform.exit();
            return;
        }

        // --- Measurements ---
        viewMeasurements = new MeasurementsView();
        viewMeasurements.setPanels(panels);

        // --- 2D ---
        view2d = new Canvas2DView();
        view2d.setPanels(panels);
        view2d.setSeamMeasurements(viewMeasurements);
        view2d.setSvgDocument(svgDocument);

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
            } catch (Exception e) {
                // If reload fails due to missing IDs, we could potentially re-launch wizard
                // For now, ignore reload failures - user can fix svg and save again
            }
        });
    }

    /**
     * Try to load panels, launch wizard if required IDs are missing.
     * Returns null if user cancelled wizard.
     * Also sets the svgDocument field for export functionality.
     */
    private List<PanelCurves> loadPanelsOrLaunchWizard(Stage stage, Path path) {
        try {
            // Load the SVG document
            SvgLoader loader = new SvgLoader();
            svgDocument = loader.load(path);
            
            // Extract panels from the document
            PatternContract contract = new PatternContract();
            PathSampler sampler = new PathSampler();
            PatternExtractor extractor = new PatternExtractor(contract, sampler);
            
            return extractor.extractPanels(svgDocument, 0.2, 0.5);
        } catch (Exception e) {
            // Check if this is a missing ID exception
            if (e instanceof IllegalStateException && e.getMessage() != null && 
                e.getMessage().contains("Missing required SVG element id=")) {
                
                // Launch wizard
                boolean success = launchWizard(stage, path);
                if (!success) {
                    return null;
                }
                
                // Try loading again with the new file
                try {
                    SvgLoader loader = new SvgLoader();
                    svgDocument = loader.load(svgPath);
                    
                    PatternContract contract = new PatternContract();
                    PathSampler sampler = new PathSampler();
                    PatternExtractor extractor = new PatternExtractor(contract, sampler);
                    
                    return extractor.extractPanels(svgDocument, 0.2, 0.5);
                } catch (Exception ex) {
                    showError("Failed to load SVG after wizard completion", ex.getMessage());
                    return null;
                }
            } else {
                // Some other error
                showError("Failed to load SVG", e.getMessage());
                return null;
            }
        }
    }

    /**
     * Launch the ID assignment wizard.
     * Returns true if wizard completed successfully, false if cancelled.
     */
    private boolean launchWizard(Stage stage, Path path) {
        try {
            // Load SVG document
            SvgLoader loader = new SvgLoader();
            SvgDocument doc = loader.load(path);
            
            // Create wizard session
            IdWizardSession session = new IdWizardSession(doc.getDocument());
            
            // Check if there are actually missing steps
            if (session.totalMissing() == 0) {
                // No missing IDs, shouldn't have gotten here
                return true;
            }
            
            // Show wizard dialog
            IdAssignmentWizard wizard = new IdAssignmentWizard(session);
            Optional<Boolean> result = wizard.showAndWait();
            
            if (result.isPresent() && result.get()) {
                // Wizard completed - save modified SVG
                String originalName = path.getFileName().toString();
                String baseName = originalName.replaceFirst("\\.svg$", "");
                String newName = baseName + CORSET_VIEWER_SUFFIX;
                Path newPath = path.getParent().resolve(newName);
                
                SvgTextEditor editor = new SvgTextEditor();
                editor.saveWithAssignments(path, newPath, session);
                
                // Update svgPath to the new file
                svgPath = newPath;
                
                return true;
            } else {
                // User cancelled
                showError("Wizard cancelled", "You must assign required IDs to load the SVG.");
                return false;
            }
        } catch (Exception e) {
            showError("Wizard error", e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private void showError(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    private void stopResources() {
        stopWatching();
        reloadExec.shutdownNow();
    }
}
