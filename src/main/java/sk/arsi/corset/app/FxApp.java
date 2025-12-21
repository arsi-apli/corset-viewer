package sk.arsi.corset.app;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import sk.arsi.corset.model.PanelCurves;
import sk.arsi.corset.svg.PathSampler;
import sk.arsi.corset.svg.PatternContract;
import sk.arsi.corset.svg.PatternExtractor;
import sk.arsi.corset.svg.SvgDocument;
import sk.arsi.corset.svg.SvgLoader;

import java.nio.file.Path;
import java.util.List;

public final class FxApp extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        List<String> args = getParameters().getRaw();
        if (args.size() != 1) {
            throw new IllegalArgumentException("Expected 1 arg: path to SVG");
        }

        Path svgPath = Path.of(args.get(0));

        SvgLoader loader = new SvgLoader();
        SvgDocument doc = loader.load(svgPath);

        PatternContract contract = new PatternContract();
        PathSampler sampler = new PathSampler();
        PatternExtractor extractor = new PatternExtractor(contract, sampler);

        double flatnessMm = 0.2;
        double resampleStepMm = 0.5;

        List<PanelCurves> panels = extractor.extractPanels(doc, flatnessMm, resampleStepMm);

        // --- 2D ---
        Canvas2DView view2d = new Canvas2DView();
        view2d.setPanels(panels);

//        // --- 3D ---
//        Corset3DView view3d = new Corset3DView();
//        view3d.setPanels(panels);
        // --- Tabs ---
        Tab tab2d = new Tab("2D");
        tab2d.setClosable(false);
        tab2d.setContent(view2d.getNode());

//        Tab tab3d = new Tab("3D");
//        tab3d.setClosable(false);
//        tab3d.setContent(view3d.getNode());
        TabPane tabs = new TabPane(tab2d);

        BorderPane root = new BorderPane();
        root.setCenter(tabs);

        Scene scene = new Scene(root, 1200, 700);
        stage.setTitle("Corset Viewer");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
