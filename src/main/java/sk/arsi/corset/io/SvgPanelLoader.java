package sk.arsi.corset.io;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import sk.arsi.corset.model.PanelCurves;
import sk.arsi.corset.svg.PathSampler;
import sk.arsi.corset.svg.PatternContract;
import sk.arsi.corset.svg.PatternExtractor;
import sk.arsi.corset.svg.SvgDocument;
import sk.arsi.corset.svg.SvgLoader;

public final class SvgPanelLoader {

    private final double flatnessMm;
    private final double resampleStepMm;

    public SvgPanelLoader(double flatnessMm, double resampleStepMm) {
        this.flatnessMm = flatnessMm;
        this.resampleStepMm = resampleStepMm;
    }

    public List<PanelCurves> loadPanelsWithRetry(Path svgPath, int attempts, long retryDelayMs) throws Exception {
        Exception last = null;

        for (int i = 1; i <= attempts; i++) {
            try {
                SvgLoader loader = new SvgLoader();
                SvgDocument doc = loader.load(svgPath);
                
                // Read max panel from metadata, default to F if missing
                Optional<Character> maxPanelOpt = doc.readMaxPanelMetadata();
                char maxPanel = maxPanelOpt.orElse('F');

                PatternContract contract = new PatternContract(maxPanel);
                PathSampler sampler = new PathSampler();
                PatternExtractor extractor = new PatternExtractor(contract, sampler);

                return extractor.extractPanels(doc, flatnessMm, resampleStepMm);
            } catch (Exception e) {
                last = e;
                if (i < attempts) {
                    try {
                        Thread.sleep(retryDelayMs);
                    } catch (InterruptedException ignored) {
                    }
                }
            }
        }

        throw last;
    }
}
