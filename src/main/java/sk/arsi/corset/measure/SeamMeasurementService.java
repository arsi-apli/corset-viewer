package sk.arsi.corset.measure;

import sk.arsi.corset.measure.MeasurementUtils.SeamSide;
import sk.arsi.corset.measure.MeasurementUtils.SeamSplit;
import sk.arsi.corset.model.PanelCurves;
import sk.arsi.corset.model.PanelId;

import java.util.ArrayList;
import java.util.List;

/**
 * Service class to compute all seam measurements for a set of panels.
 */
public final class SeamMeasurementService {

    private SeamMeasurementService() {
    }

    /**
     * Compute all seam measurements for panels A-F.
     * Returns a list of SeamMeasurementData for seam pairs: AB, BC, CD, DE, EF.
     */
    public static List<SeamMeasurementData> computeAllSeamMeasurements(List<PanelCurves> panels) {
        if (panels == null || panels.isEmpty()) {
            return new ArrayList<>();
        }

        PanelId[] ids = {PanelId.A, PanelId.B, PanelId.C, PanelId.D, PanelId.E, PanelId.F};
        List<SeamMeasurementData> results = new ArrayList<>();

        for (int i = 0; i < ids.length - 1; i++) {
            PanelId leftId = ids[i];
            PanelId rightId = ids[i + 1];
            
            PanelCurves left = findPanel(panels, leftId);
            PanelCurves right = findPanel(panels, rightId);

            SeamMeasurementData data = computeSeamPairMeasurement(leftId, rightId, left, right);
            results.add(data);
        }

        return results;
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
        if (panels == null) {
            return null;
        }
        for (PanelCurves p : panels) {
            if (p.getPanelId() == id) {
                return p;
            }
        }
        return null;
    }
}
