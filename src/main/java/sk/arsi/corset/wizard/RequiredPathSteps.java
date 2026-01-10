package sk.arsi.corset.wizard;

import sk.arsi.corset.model.PanelId;
import sk.arsi.corset.svg.PatternContract;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic generator of required wizard steps for A..maxPanel.
 */
public final class RequiredPathSteps {

    private RequiredPathSteps() {
    }

    public static List<RequiredPath> steps(char maxPanel) {
        PatternContract contract = new PatternContract(maxPanel);
        List<RequiredPath> out = new ArrayList<>();

        for (PanelId p : PanelId.rangeInclusive(contract.getMaxPanel())) {
            out.add(new RequiredPath(contract.topId(p)));
            out.add(new RequiredPath(contract.waistId(p)));
            out.add(new RequiredPath(contract.bottomId(p)));

            String prevBase = contract.seamToPrevId(p);
            out.add(new RequiredPath(contract.seamUpId(prevBase)));
            out.add(new RequiredPath(contract.seamDownId(prevBase)));

            String nextBase = contract.seamToNextId(p);
            out.add(new RequiredPath(contract.seamUpId(nextBase)));
            out.add(new RequiredPath(contract.seamDownId(nextBase)));
        }

        return out;
    }
}
