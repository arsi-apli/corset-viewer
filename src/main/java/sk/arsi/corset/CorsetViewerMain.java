package sk.arsi.corset;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sk.arsi.corset.model.PanelCurves;
import sk.arsi.corset.svg.*;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

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
        
        // Read max panel from metadata, default to F if missing
        Optional<Character> maxPanelOpt = doc.readMaxPanelMetadata();
        char maxPanel = maxPanelOpt.orElse('F');
        
        if (!maxPanelOpt.isPresent()) {
            LOG.warn("No panel count metadata found in SVG. Using default: A-{}", maxPanel);
            LOG.warn("Add '{}=\"{}\"' attribute to the <svg> root element to set panel range.", 
                SvgDocument.ATTR_MAX_PANEL, maxPanel);
        } else {
            LOG.info("Panel count from metadata: A-{}", maxPanel);
        }

        PatternContract contract = new PatternContract(maxPanel);
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
