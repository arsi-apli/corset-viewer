package sk.arsi.corset;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sk.arsi.corset.model.PanelCurves;
import sk.arsi.corset.svg.*;

import java.nio.file.Path;
import java.util.List;

public final class CorsetViewerMain {

    private static final Logger LOG = LoggerFactory.getLogger(CorsetViewerMain.class);

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Usage: java -jar corset-viewer.jar <pattern.svg>");
            System.exit(2);
        }

        Path svgPath = Path.of(args[0]);

        SvgLoader loader = new SvgLoader();
        SvgDocument doc = loader.load(svgPath);

        PatternContract contract = new PatternContract();
        PathSampler sampler = new PathSampler();
        PatternExtractor extractor = new PatternExtractor(contract, sampler);

        double flatnessMm = 0.2; // neskôr dáme do configu/UI
        List<PanelCurves> panels = extractor.extractPanels(doc, flatnessMm);

        for (int i = 0; i < panels.size(); i++) {
            PanelCurves p = panels.get(i);
            LOG.info("Panel {}: TOP={}pts WAIST={}pts BOTTOM={}pts",
                    p.getPanelId().name(),
                    Integer.valueOf(p.getTop().getPoints().size()),
                    Integer.valueOf(p.getWaist().getPoints().size()),
                    Integer.valueOf(p.getBottom().getPoints().size())
            );
        }

        LOG.info("OK. Loaded {} panels.", Integer.valueOf(panels.size()));
    }
}
