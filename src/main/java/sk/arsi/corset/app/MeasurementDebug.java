package sk.arsi.corset.app;

// Debug helper — put into a test runner or main
import sk.arsi.corset.model.PanelCurves;
import sk.arsi.corset.model.Curve2D;
import sk.arsi.corset.model.PanelId;
import sk.arsi.corset.measure.MeasurementUtils;
import sk.arsi.corset.resize.SvgPathEditor;
import java.util.List;

public class MeasurementDebug {

    public static void debugAllPanels(List<PanelCurves> panels, double dyMm) {
        if (panels == null) {
            System.out.println("No panels");
            return;
        }
        System.out.println("Debug measurement at dyMm = " + dyMm);
        for (PanelCurves p : panels) {
            if (p == null) {
                continue;
            }
            PanelId pid = p.getPanelId();
            System.out.println("=== Panel " + (pid == null ? "<null id>" : pid.name()) + " ===");
            double waistY = MeasurementUtils.computePanelWaistY0(p.getWaist());
            double y = waistY - dyMm;
            System.out.println("waistY=" + waistY + " -> measurement y=" + y);

            // Helper to print a seam curve
            java.util.function.Consumer<Curve2D> printCurve = (curve) -> {
                if (curve == null) {
                    System.out.println("  curve: <null>");
                    return;
                }
                System.out.println("  id=" + curve.getId());
                System.out.println("  d   = " + curve.getD());
                try {
                    String norm = SvgPathEditor.normalizePath(curve.getD());
                    System.out.println("  norm= " + norm);
                } catch (Exception ex) {
                    System.out.println("  norm failed: " + ex);
                }
                try {
                    List<Double> xsAnalytic = MeasurementUtils.intersectHorizontalXsFromPathDAnalytic(curve.getD(), y);
                    System.out.println("  analytic xs (from d) = " + xsAnalytic);
                } catch (Throwable t) {
                    System.out.println("  analytic error: " + t);
                }
                try {
                    List<Double> xs = MeasurementUtils.intersectHorizontalXs(curve, y);
                    System.out.println("  intersectHorizontalXs (full API) = " + xs);
                } catch (Throwable t) {
                    System.out.println("  intersect API error: " + t);
                }
            };

            // choose the seams that computePanelWidthAtDy would pick
            Curve2D leftUp = p.getSeamToPrevUp();
            Curve2D leftDown = p.getSeamToPrevDown();
            Curve2D rightUp = p.getSeamToNextUp();
            Curve2D rightDown = p.getSeamToNextDown();

            System.out.println("Left (prefer up):");
            printCurve.accept(leftUp);
            System.out.println("Left (fallback down):");
            printCurve.accept(leftDown);
            System.out.println("Right (prefer up):");
            printCurve.accept(rightUp);
            System.out.println("Right (fallback down):");
            printCurve.accept(rightDown);

            // Show which curves preferNonEmpty would choose
            Curve2D leftChosen = (dyMm >= 0)
                    ? (leftUp != null && leftUp.getPoints() != null && !leftUp.getPoints().isEmpty() ? leftUp : leftDown)
                    : (leftDown != null && leftDown.getPoints() != null && !leftDown.getPoints().isEmpty() ? leftDown : leftUp);
            Curve2D rightChosen = (dyMm >= 0)
                    ? (rightUp != null && rightUp.getPoints() != null && !rightUp.getPoints().isEmpty() ? rightUp : rightDown)
                    : (rightDown != null && rightDown.getPoints() != null && !rightDown.getPoints().isEmpty() ? rightDown : rightUp);

            System.out.println("Left chosen id=" + (leftChosen == null ? "<null>" : leftChosen.getId()));
            System.out.println("Right chosen id=" + (rightChosen == null ? "<null>" : rightChosen.getId()));

            try {
                java.util.OptionalDouble w = MeasurementUtils.computePanelWidthAtDy(p, dyMm);
                System.out.println("Computed width = " + (w.isPresent() ? w.getAsDouble() : "<empty>"));
            } catch (Throwable t) {
                System.out.println("computePanelWidthAtDy threw: " + t);
            }

            System.out.println();
        }
    }
}
