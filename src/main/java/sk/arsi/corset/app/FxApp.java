package sk.arsi.corset.app;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sk.arsi.corset.layout.ChainLayoutEngine.EdgeMode;
import sk.arsi.corset.model.PanelCurves;
import sk.arsi.corset.svg.PathSampler;
import sk.arsi.corset.svg.PatternContract;
import sk.arsi.corset.svg.PatternExtractor;
import sk.arsi.corset.svg.SvgDocument;
import sk.arsi.corset.svg.SvgLoader;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class FxApp extends Application {

    private static final Logger LOG = LoggerFactory.getLogger(FxApp.class);

    private Path svgPath;
    private double flatnessMm = 0.2;
    private double resampleStepMm = 0.5;

    private Canvas2DView view2d;
    private Pseudo3DView viewPseudo3d;
    private TabPane tabs;
    private SvgFileWatcher fileWatcher;

    @Override
    public void start(Stage stage) throws Exception {
        List<String> args = getParameters().getRaw();
        if (args.size() != 1) {
            throw new IllegalArgumentException("Expected 1 arg: path to SVG");
        }

        svgPath = Path.of(args.get(0));

        // Initial load
        List<PanelCurves> panels = loadSvgSync();

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

        tabs = new TabPane(tab2d, tabPseudo3d);

        BorderPane root = new BorderPane();
        root.setCenter(tabs);

        Scene scene = new Scene(root, 1200, 700);
        stage.setTitle("Corset Viewer - " + svgPath.getFileName());
        stage.setScene(scene);
        stage.show();

        // Start file watcher
        startFileWatcher();

        // Ensure watcher is stopped on close
        stage.setOnCloseRequest(e -> stopFileWatcher());
    }

    @Override
    public void stop() throws Exception {
        stopFileWatcher();
        super.stop();
    }

    /**
     * Loads SVG synchronously (blocks until complete).
     */
    private List<PanelCurves> loadSvgSync() throws Exception {
        SvgLoader loader = new SvgLoader();
        SvgDocument doc = loader.load(svgPath);

        PatternContract contract = new PatternContract();
        PathSampler sampler = new PathSampler();
        PatternExtractor extractor = new PatternExtractor(contract, sampler);

        return extractor.extractPanels(doc, flatnessMm, resampleStepMm);
    }

    /**
     * Reloads the SVG file and updates both views while preserving state.
     * This method is called on the file watcher's scheduler thread.
     */
    private void reloadSvg() {
        // Capture current state before reload
        EdgeMode currentEdgeMode = viewPseudo3d.getEdgeMode();
        Tab selectedTab = tabs.getSelectionModel().getSelectedItem();

        // Load in background
        CompletableFuture.runAsync(() -> {
            try {
                List<PanelCurves> panels = loadSvgSync();
                
                // Update UI on JavaFX thread
                Platform.runLater(() -> {
                    try {
                        // Preserve Pseudo3D mode before updating panels
                        viewPseudo3d.setEdgeMode(currentEdgeMode);
                        
                        // Update both views
                        view2d.setPanels(panels);
                        viewPseudo3d.setPanels(panels);
                        
                        // Restore tab selection
                        if (selectedTab != null) {
                            tabs.getSelectionModel().select(selectedTab);
                        }
                        
                        LOG.info("Views refreshed successfully");
                    } catch (Exception e) {
                        LOG.error("Error updating UI after reload", e);
                    }
                });
            } catch (Exception e) {
                LOG.error("Error reloading SVG", e);
                throw new RuntimeException("Failed to reload SVG", e);
            }
        });
    }

    private void startFileWatcher() {
        if (svgPath == null) {
            return;
        }
        
        try {
            fileWatcher = new SvgFileWatcher(svgPath, this::reloadSvg);
            fileWatcher.start();
        } catch (Exception e) {
            LOG.error("Failed to start file watcher", e);
        }
    }

    private void stopFileWatcher() {
        if (fileWatcher != null) {
            fileWatcher.stop();
            fileWatcher = null;
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
