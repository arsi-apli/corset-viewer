package sk.arsi.corset.measure;

import sk.arsi.corset.measure.MeasurementUtils.SeamSide;
import sk.arsi.corset.measure.MeasurementUtils.SeamSplit;
import sk.arsi.corset.model.PanelCurves;
import sk.arsi.corset.model.PanelId;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Service class to compute all seam measurements for a set of panels.
 */
public final class SeamMeasurementService {

    private SeamMeasurementService() {
    }

    /**
     * Compute seam measurements for all adjacent panel pairs present in the
     * input list. For panels A..max, returns seam pairs: AB, BC, CD, ...
     *
     * If panels are missing (e.g. C missing), the corresponding pair
     * measurement will be returned with zeros (same behavior as original code
     * for missing panels).
     */
    public static List<SeamMeasurementData> computeAllSeamMeasurements(List<PanelCurves> panels) {
        if (panels == null || panels.isEmpty()) {
            return new ArrayList<>();
        }

        // Collect unique panel IDs from provided panels
        List<PanelId> ids = collectSortedPanelIds(panels);
        if (ids.size() < 2) {
            return new ArrayList<>();
        }

        List<SeamMeasurementData> results = new ArrayList<>();

        for (int i = 0; i < ids.size() - 1; i++) {
            PanelId leftId = ids.get(i);
            PanelId rightId = ids.get(i + 1);

            PanelCurves left = findPanel(panels, leftId);
            PanelCurves right = findPanel(panels, rightId);

            SeamMeasurementData data = computeSeamPairMeasurement(leftId, rightId, left, right);
            results.add(data);
        }

        return results;
    }

    private static List<PanelId> collectSortedPanelIds(List<PanelCurves> panels) {
        List<PanelId> ids = new ArrayList<>();
        for (PanelCurves p : panels) {
            if (p == null) {
                continue;
            }
            PanelId id = p.getPanelId();
            if (id == null) {
                continue;
            }

            // ensure unique
            boolean exists = false;
            for (PanelId existing : ids) {
                if (id.equals(existing)) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                ids.add(id);
            }
        }

        ids.sort(Comparator.comparingInt(PanelId::letter));
        return ids;
    }

    private static SeamMeasurementData computeSeamPairMeasurement(
            PanelId leftId,
            PanelId rightId,
            PanelCurves left,
            PanelCurves right) {

        String name = leftId.name() + rightId.name();

        if (left == null || right == null) {
            return new SeamMeasurementData(
                    name, leftId, rightId,
                    0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
                    0.0, 0.0, 0.0, 0.0, 0.0, 0.0
            );
        }

        // Measure seam splits
        SeamSplit lUp = MeasurementUtils.measureSeamSplitAtWaist(left, SeamSide.TO_NEXT, true);
        SeamSplit lDn = MeasurementUtils.measureSeamSplitAtWaist(left, SeamSide.TO_NEXT, false);
        SeamSplit rUp = MeasurementUtils.measureSeamSplitAtWaist(right, SeamSide.TO_PREV, true);
        SeamSplit rDn = MeasurementUtils.measureSeamSplitAtWaist(right, SeamSide.TO_PREV, false);

        // TOP table measures from waist upwards (portion above waist)
        double leftUpTop = lUp.above;
        double rightUpTop = rUp.above;
        double diffUpTop = leftUpTop - rightUpTop;

        double leftDownTop = lDn.above;
        double rightDownTop = rDn.above;
        double diffDownTop = leftDownTop - rightDownTop;

        // BOTTOM table measures from waist downwards (portion below waist)
        double leftUpBottom = lUp.below;
        double rightUpBottom = rUp.below;
        double diffUpBottom = leftUpBottom - rightUpBottom;

        double leftDownBottom = lDn.below;
        double rightDownBottom = rDn.below;
        double diffDownBottom = leftDownBottom - rightDownBottom;

        return new SeamMeasurementData(
                name, leftId, rightId,
                leftUpTop, rightUpTop, diffUpTop,
                leftDownTop, rightDownTop, diffDownTop,
                leftUpBottom, rightUpBottom, diffUpBottom,
                leftDownBottom, rightDownBottom, diffDownBottom
        );
    }

    private static PanelCurves findPanel(List<PanelCurves> panels, PanelId id) {
        if (panels == null || id == null) {
            return null;
        }
        for (PanelCurves p : panels) {
            if (p == null) {
                continue;
            }
            PanelId pid = p.getPanelId();
            if (id.equals(pid)) {
                return p;
            }
        }
        return null;
    }
}
