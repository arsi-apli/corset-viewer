package sk.arsi.corset.layout;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sk.arsi.corset.model.Curve2D;
import sk.arsi.corset.model.PanelCurves;
import sk.arsi.corset.model.Pt;

/**
 * Computes chained panel layout for pseudo-3D visualization. Chains panels
 * along TOP or BOTTOM edge with rotation and translation.
 */
public final class ChainLayoutEngine {

    private static final Logger logger = LoggerFactory.getLogger(ChainLayoutEngine.class);

    public enum EdgeMode {
        TOP,
        BOTTOM
    }

    public static final double FALLBACK_PANEL_SPACING_MM = 10.0;

    /**
     * 2D rigid transform defined by rotation around a pivot and a translation.
     */
    public static final class Transform2D {

        private final double angleRad;
        private final double pivotX;
        private final double pivotY;
        private final double tx;
        private final double ty;

        public Transform2D(double angleRad, double pivotX, double pivotY, double tx, double ty) {
            this.angleRad = angleRad;
            this.pivotX = pivotX;
            this.pivotY = pivotY;
            this.tx = tx;
            this.ty = ty;
        }

        public Pt apply(Pt p) {
            // Rotate around pivot
            double cos = Math.cos(angleRad);
            double sin = Math.sin(angleRad);

            double x0 = p.getX() - pivotX;
            double y0 = p.getY() - pivotY;

            double xr = x0 * cos - y0 * sin;
            double yr = x0 * sin + y0 * cos;

            double outX = xr + pivotX + tx;
            double outY = yr + pivotY + ty;

            return new Pt(outX, outY);
        }

        /**
         * Create a new transform with additional translation applied. The
         * rotation and pivot remain the same.
         */
        public Transform2D withAdditionalTranslation(double deltaTx, double deltaTy) {
            return new Transform2D(angleRad, pivotX, pivotY, tx + deltaTx, ty + deltaTy);
        }

        public double getAngleRad() {
            return angleRad;
        }

        public double getPivotX() {
            return pivotX;
        }

        public double getPivotY() {
            return pivotY;
        }

        public double getTx() {
            return tx;
        }

        public double getTy() {
            return ty;
        }
    }

    public static final class LayoutResult {

        private final PanelCurves panel;
        private final Transform2D transform;

        public LayoutResult(PanelCurves panel, Transform2D transform) {
            this.panel = panel;
            this.transform = transform;
        }

        public PanelCurves getPanel() {
            return panel;
        }

        public Transform2D getTransform() {
            return transform;
        }
    }

    public List<LayoutResult> computeLayout(List<PanelCurves> panels, EdgeMode mode) {
        List<LayoutResult> results = new ArrayList<>();
        if (panels == null || panels.isEmpty()) {
            return results;
        }

        // Track previous panel's joint and edge right in world space
        Pt prevJointWorld = null;
        Pt prevEdgeRightWorld = null;

        // Track previous panel and transform for seam snapping
        PanelCurves prevPanel = null;
        Transform2D prevTransform = null;

        for (int i = 0; i < panels.size(); i++) {
            PanelCurves panel = panels.get(i);

            Curve2D waist = panel.getWaist();
            Pt waistLeft = pickExtremeX(waist, true);
            Pt waistRight = pickExtremeX(waist, false);

            Curve2D edge = (mode == EdgeMode.TOP) ? panel.getTop() : panel.getBottom();
            Pt edgeLeft = pickExtremeX(edge, true);
            Pt edgeRight = pickExtremeX(edge, false);

            // Fallback if missing curves
            if (waistLeft == null || waistRight == null || edgeLeft == null || edgeRight == null) {
                logger.debug("Missing anchors for panel {} (waist/edge). Using fallback spacing.", panel.getPanelId());
                double tx = (i == 0) ? 0.0 : (prevJointWorld.getX() + FALLBACK_PANEL_SPACING_MM);
                double ty = 0.0;
                Transform2D transform = new Transform2D(0.0, 0.0, 0.0, tx, ty);
                results.add(new LayoutResult(panel, transform));

                if (i == 0) {
                    prevJointWorld = new Pt(tx + FALLBACK_PANEL_SPACING_MM, ty);
                } else {
                    prevJointWorld = new Pt(tx + FALLBACK_PANEL_SPACING_MM, ty);
                }
                prevEdgeRightWorld = null;
                prevPanel = panel;
                prevTransform = transform;
                continue;
            }

            if (i == 0) {
                // Place first panel: translate waistLeft to world origin (0,0)
                double tx = -waistLeft.getX();
                double ty = -waistLeft.getY();
                Transform2D transform = new Transform2D(0.0, waistLeft.getX(), waistLeft.getY(), tx, ty);
                results.add(new LayoutResult(panel, transform));

                prevJointWorld = transform.apply(waistRight);
                prevEdgeRightWorld = transform.apply(edgeRight);
                prevPanel = panel;
                prevTransform = transform;
                continue;
            }

            // For subsequent panels:
            // 1) rotate around joint (waistLeft) so that vector (joint->edgeLeft) aligns with previous vector (prevJoint->prevEdgeRight)
            // 2) translate so joint coincides with prevJointWorld (which is prev waistRight in world)
            Pt joint = waistLeft;

            Pt vLocal = new Pt(edgeLeft.getX() - joint.getX(), edgeLeft.getY() - joint.getY());
            Pt vPrevWorld;
            if (prevJointWorld != null && prevEdgeRightWorld != null) {
                vPrevWorld = new Pt(prevEdgeRightWorld.getX() - prevJointWorld.getX(), prevEdgeRightWorld.getY() - prevJointWorld.getY());
            } else {
                // fallback vector if previous edge right missing
                vPrevWorld = new Pt(1.0, 0.0);
            }

            double angleLocal = Math.atan2(vLocal.getY(), vLocal.getX());
            double anglePrev = Math.atan2(vPrevWorld.getY(), vPrevWorld.getX());
            double angleRad = anglePrev - angleLocal;

            // Translate joint to prevJointWorld after rotation
            double jointX = joint.getX();
            double jointY = joint.getY();

            // Apply rotation around joint; compute rotated joint world position (it stays at same coords before translation)
            // So translation is just (prevJointWorld - joint) in world space when no prior translation.
            double tx = prevJointWorld.getX() - jointX;
            double ty = prevJointWorld.getY() - jointY;

            Transform2D transform = new Transform2D(angleRad, jointX, jointY, tx, ty);

            // 5. Apply seam snapping (waist-Y-aware endpoint selection)
            if (prevPanel != null && prevTransform != null) {
                transform = applySeamSnapping(prevPanel, prevTransform, panel, transform, mode);
            }

            results.add(new LayoutResult(panel, transform));

            // Update world positions for next iteration
            prevJointWorld = transform.apply(waistRight);
            prevEdgeRightWorld = transform.apply(edgeRight);
            prevPanel = panel;
            prevTransform = transform;
        }

        return results;
    }

    /**
     * Apply seam snapping to align seam endpoints between adjacent panels.
     *
     * The rotation is kept unchanged; only an additional translation is
     * applied.
     *
     * Endpoint selection is waist-Y-aware to avoid snapping to the waist
     * endpoint when seam point order/orientation differs across panels.
     */
    private Transform2D applySeamSnapping(
            PanelCurves prevPanel,
            Transform2D prevTransform,
            PanelCurves curPanel,
            Transform2D curTransform,
            EdgeMode mode
    ) {
        // NOTE: This method still assumes the panels passed in are in A→F order (prev uses seamToNext, cur uses seamToPrev).
        // If order detection produces reversed order, seam pairing should be updated accordingly.
        Curve2D prevSeam = selectSeamCurve(prevPanel.getSeamToNextDown(), prevPanel.getSeamToNextUp(), mode);
        Curve2D curSeam = selectSeamCurve(curPanel.getSeamToPrevDown(), curPanel.getSeamToPrevUp(), mode);

        if (prevSeam == null || curSeam == null) {
            logger.debug("Skipping seam snap: seam curves not available");
            return curTransform;
        }

        Pt prevWaistRight = pickExtremeX(prevPanel.getWaist(), false);
        Pt curWaistLeft = pickExtremeX(curPanel.getWaist(), true);
        if (prevWaistRight == null || curWaistLeft == null) {
            logger.debug("Skipping seam snap: waist anchors not available");
            return curTransform;
        }

        Pt prevAnchorLocal = getSeamAnchor(prevSeam, prevWaistRight.getY(), mode);
        Pt curAnchorLocal = getSeamAnchor(curSeam, curWaistLeft.getY(), mode);

        if (prevAnchorLocal == null || curAnchorLocal == null) {
            logger.debug("Skipping seam snap: anchor points not available");
            return curTransform;
        }

        Pt prevAnchorWorld = prevTransform.apply(prevAnchorLocal);
        Pt curAnchorWorld = curTransform.apply(curAnchorLocal);

        double deltaX = prevAnchorWorld.getX() - curAnchorWorld.getX();
        double deltaY = prevAnchorWorld.getY() - curAnchorWorld.getY();

        // Apply delta as additional translation
        Transform2D snappedTransform = curTransform.withAdditionalTranslation(deltaX, deltaY);

        logger.debug("Applied seam snap: delta=({}, {})", deltaX, deltaY);
        return snappedTransform;
    }

    /**
     * Select the appropriate seam curve based on EdgeMode. For BOTTOM mode:
     * prefer DOWN seam, fallback to UP. For TOP mode: prefer UP seam, fallback
     * to DOWN.
     */
    private Curve2D selectSeamCurve(Curve2D seamDown, Curve2D seamUp, EdgeMode mode) {
        if (mode == EdgeMode.BOTTOM) {
            // BOTTOM mode: prefer DOWN seam
            if (seamDown != null && hasValidPoints(seamDown)) {
                return seamDown;
            }
            if (seamUp != null && hasValidPoints(seamUp)) {
                return seamUp;
            }
        } else {
            // TOP mode: prefer UP seam
            if (seamUp != null && hasValidPoints(seamUp)) {
                return seamUp;
            }
            if (seamDown != null && hasValidPoints(seamDown)) {
                return seamDown;
            }
        }
        return null;
    }

    private boolean hasValidPoints(Curve2D curve) {
        return curve != null && curve.getPoints() != null && !curve.getPoints().isEmpty();
    }

    /**
     * Waist-Y-aware seam endpoint selection: - TOP: prefer endpoint with y < waistY; fallback to smaller y
     * - BOTTOM: prefer endpoint with y > waistY; fallback to larger y
     */
    private Pt getSeamAnchor(Curve2D seam, double waistY, EdgeMode mode) {
        if (seam == null) {
            return null;
        }
        List<Pt> points = seam.getPoints();
        if (points == null || points.isEmpty()) {
            return null;
        }
        Pt first = points.get(0);
        if (points.size() == 1) {
            return first;
        }
        Pt last = points.get(points.size() - 1);

        if (mode == EdgeMode.TOP) {
            boolean firstAbove = first.getY() < waistY;
            boolean lastAbove = last.getY() < waistY;

            if (firstAbove && !lastAbove) {
                return first;
            }
            if (lastAbove && !firstAbove) {
                return last;
            }
            // fallback: smaller y (higher)
            return (first.getY() <= last.getY()) ? first : last;
        } else {
            boolean firstBelow = first.getY() > waistY;
            boolean lastBelow = last.getY() > waistY;

            if (firstBelow && !lastBelow) {
                return first;
            }
            if (lastBelow && !firstBelow) {
                return last;
            }
            // fallback: larger y (lower)
            return (first.getY() >= last.getY()) ? first : last;
        }
    }

    /**
     * Pick point from curve by extreme X (minX if left=true, else maxX).
     */
    private Pt pickExtremeX(Curve2D curve, boolean left) {
        if (curve == null || curve.getPoints() == null || curve.getPoints().isEmpty()) {
            return null;
        }
        Pt best = null;
        for (Pt p : curve.getPoints()) {
            if (best == null) {
                best = p;
            } else {
                if (left) {
                    if (p.getX() < best.getX()) {
                        best = p;
                    }
                } else {
                    if (p.getX() > best.getX()) {
                        best = p;
                    }
                }
            }
        }
        return best;
    }
}
