package sk.arsi.corset.layout;

import sk.arsi.corset.model.Curve2D;
import sk.arsi.corset.model.PanelCurves;
import sk.arsi.corset.model.Pt;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Computes chained panel layout for pseudo-3D visualization.
 * Chains panels along TOP or BOTTOM edge with rotation and translation.
 */
public final class ChainLayoutEngine {

    private static final Logger logger = LoggerFactory.getLogger(ChainLayoutEngine.class);

    public enum EdgeMode {
        TOP,
        BOTTOM
    }

    // Fallback spacing when panel data is missing
    private static final double FALLBACK_PANEL_SPACING_MM = 150.0;

    /**
     * Represents a 2D transform (rotation + translation).
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
            if (p == null) {
                return null;
            }

            double x = p.getX();
            double y = p.getY();

            double dx = x - pivotX;
            double dy = y - pivotY;

            double cos = Math.cos(angleRad);
            double sin = Math.sin(angleRad);

            double rx = cos * dx - sin * dy;
            double ry = sin * dx + cos * dy;

            double outX = pivotX + rx + tx;
            double outY = pivotY + ry + ty;
            return new Pt(outX, outY);
        }

        /**
         * Create a new transform with additional translation applied.
         * The rotation and pivot remain the same.
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

    /**
     * Result of layout computation for a single panel.
     */
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

    /**
     * Compute chained layout for a list of panels.
     * 
     * @param panels List of panels in the desired chain order
     * @param mode Edge mode (TOP or BOTTOM)
     * @return List of layout results with transforms
     */
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

            // Get waist curve for joint anchors
            Curve2D waist = panel.getWaist();
            Pt waistLeft = extremeByX(waist, true);
            Pt waistRight = extremeByX(waist, false);

            // Get edge curve (TOP or BOTTOM)
            Curve2D edge = (mode == EdgeMode.TOP) ? panel.getTop() : panel.getBottom();
            Pt edgeLeft = extremeByX(edge, true);
            Pt edgeRight = extremeByX(edge, false);

            // Handle missing data
            if (waistLeft == null || waistRight == null || edgeLeft == null || edgeRight == null) {
                // Fallback: simple translation
                double tx = (prevJointWorld != null) ? prevJointWorld.getX() + FALLBACK_PANEL_SPACING_MM : 0.0;
                double ty = (prevJointWorld != null) ? prevJointWorld.getY() : 0.0;
                Transform2D transform = new Transform2D(0.0, 0.0, 0.0, tx, ty);
                results.add(new LayoutResult(panel, transform));
                
                if (waistRight != null) {
                    prevJointWorld = transform.apply(waistRight);
                } else {
                    prevJointWorld = new Pt(tx + FALLBACK_PANEL_SPACING_MM, ty);
                }
                prevEdgeRightWorld = null;
                prevPanel = panel;
                prevTransform = transform;
                continue;
            }

            Transform2D transform;

            if (i == 0) {
                // First panel: translate so waistLeft is at (0, 0)
                double tx = -waistLeft.getX();
                double ty = -waistLeft.getY();
                transform = new Transform2D(0.0, 0.0, 0.0, tx, ty);

                // Update world positions
                prevJointWorld = transform.apply(waistRight);
                prevEdgeRightWorld = transform.apply(edgeRight);
            } else {
                // Subsequent panels: rotate and translate
                // 1. Compute current vectors in local space
                double jointX = waistLeft.getX();
                double jointY = waistLeft.getY();
                double edgeLeftX = edgeLeft.getX();
                double edgeLeftY = edgeLeft.getY();

                double vecLocalX = edgeLeftX - jointX;
                double vecLocalY = edgeLeftY - jointY;

                // 2. Compute previous vector in world space
                double prevJointX = prevJointWorld.getX();
                double prevJointY = prevJointWorld.getY();
                double prevEdgeRightX = prevEdgeRightWorld.getX();
                double prevEdgeRightY = prevEdgeRightWorld.getY();

                double vecPrevX = prevEdgeRightX - prevJointX;
                double vecPrevY = prevEdgeRightY - prevJointY;

                // 3. Compute rotation angle
                double angleLocal = Math.atan2(vecLocalY, vecLocalX);
                double anglePrev = Math.atan2(vecPrevY, vecPrevX);
                double angleRad = anglePrev - angleLocal;

                // 4. Create transform: rotate around joint, then translate to align joints
                // After rotation around jointX, jointY, the joint stays at (jointX, jointY)
                // We need to translate so the joint moves to (prevJointX, prevJointY)
                double tx = prevJointX - jointX;
                double ty = prevJointY - jointY;

                transform = new Transform2D(angleRad, jointX, jointY, tx, ty);
                
                // 5. Apply seam-endpoint snapping (Variant 1)
                if (prevPanel != null && prevTransform != null) {
                    transform = applySeamSnapping(prevPanel, prevTransform, panel, transform, mode);
                }

                // Update world positions
                prevJointWorld = transform.apply(waistRight);
                prevEdgeRightWorld = transform.apply(edgeRight);
            }

            results.add(new LayoutResult(panel, transform));
            prevPanel = panel;
            prevTransform = transform;
        }

        return results;
    }

    /**
     * Apply seam-endpoint snapping to align seam endpoints between adjacent panels.
     * 
     * @param prevPanel Previous panel in chain
     * @param prevTransform Transform of previous panel
     * @param curPanel Current panel in chain
     * @param curTransform Current transform (before snapping)
     * @param mode Edge mode (TOP or BOTTOM)
     * @return Updated transform with seam snapping applied
     */
    private Transform2D applySeamSnapping(PanelCurves prevPanel, Transform2D prevTransform,
                                          PanelCurves curPanel, Transform2D curTransform,
                                          EdgeMode mode) {
        // Determine which seam curves to use based on chain direction
        // For A→F chain: prev uses seamToNext, cur uses seamToPrev
        // For F→A chain: prev uses seamToPrev, cur uses seamToNext
        // Since we don't track chain direction explicitly, we use seamToNext for prev
        // and seamToPrev for cur (this matches A→F ordering)
        Curve2D prevSeam = selectSeamCurve(prevPanel.getSeamToNextDown(), prevPanel.getSeamToNextUp(), mode);
        Curve2D curSeam = selectSeamCurve(curPanel.getSeamToPrevDown(), curPanel.getSeamToPrevUp(), mode);
        
        if (prevSeam == null || curSeam == null) {
            logger.debug("Skipping seam snap: seam curves not available");
            return curTransform;
        }
        
        // Get waist Y coordinates for endpoint selection
        // For prev panel, use waistRight (right extreme of waist curve)
        // For cur panel, use waistLeft (left extreme of waist curve)
        Pt prevWaistRight = extremeByX(prevPanel.getWaist(), false);
        Pt curWaistLeft = extremeByX(curPanel.getWaist(), true);
        
        if (prevWaistRight == null) {
            logger.debug("Skipping seam snap: prevWaistRight not available");
            return curTransform;
        }
        if (curWaistLeft == null) {
            logger.debug("Skipping seam snap: curWaistLeft not available");
            return curTransform;
        }
        
        double prevWaistY = prevWaistRight.getY();
        double curWaistY = curWaistLeft.getY();
        
        // Select anchor points based on waistY and mode
        Pt prevAnchorLocal = selectSeamEndpoint(prevSeam, prevWaistY, mode);
        Pt curAnchorLocal = selectSeamEndpoint(curSeam, curWaistY, mode);
        
        if (prevAnchorLocal == null || curAnchorLocal == null) {
            logger.debug("Skipping seam snap: anchor points not available");
            return curTransform;
        }
        
        // Transform anchors to world space
        Pt prevAnchorWorld = prevTransform.apply(prevAnchorLocal);
        Pt curAnchorWorld = curTransform.apply(curAnchorLocal);
        
        if (prevAnchorWorld == null || curAnchorWorld == null) {
            logger.debug("Skipping seam snap: failed to transform anchors");
            return curTransform;
        }
        
        // Compute delta to align anchors
        double deltaX = prevAnchorWorld.getX() - curAnchorWorld.getX();
        double deltaY = prevAnchorWorld.getY() - curAnchorWorld.getY();
        
        // Apply delta as additional translation
        Transform2D snappedTransform = curTransform.withAdditionalTranslation(deltaX, deltaY);
        
        logger.debug("Applied seam snap: delta=({}, {})", deltaX, deltaY);
        
        return snappedTransform;
    }
    
    /**
     * Select the appropriate seam curve based on EdgeMode.
     * For BOTTOM mode: prefer DOWN seam, fallback to UP.
     * For TOP mode: prefer UP seam, fallback to DOWN.
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
    
    /**
     * Check if a curve has valid points.
     */
    private boolean hasValidPoints(Curve2D curve) {
        return curve != null && curve.getPoints() != null && !curve.getPoints().isEmpty();
    }
    
    /**
     * Select seam endpoint based on waistY and edge mode.
     * 
     * For TOP mode: prefer endpoint with y < waistY; if both/none satisfy, pick endpoint with smaller y.
     * For BOTTOM mode: prefer endpoint with y > waistY; if both/none satisfy, pick endpoint with larger y.
     * 
     * @param seam Seam curve to select endpoint from
     * @param waistY Y coordinate of waist at the seam side
     * @param mode Edge mode (TOP or BOTTOM)
     * @return Selected endpoint, or null if seam has no points
     */
    private Pt selectSeamEndpoint(Curve2D seam, double waistY, EdgeMode mode) {
        if (seam == null) {
            return null;
        }
        List<Pt> points = seam.getPoints();
        if (points == null || points.isEmpty()) {
            return null;
        }
        
        // Get first and last points
        Pt first = points.get(0);
        Pt last = points.get(points.size() - 1);
        
        double firstY = first.getY();
        double lastY = last.getY();
        
        if (mode == EdgeMode.TOP) {
            // TOP mode: prefer y < waistY, else smaller y
            boolean firstBelowWaist = firstY < waistY;
            boolean lastBelowWaist = lastY < waistY;
            
            if (firstBelowWaist && !lastBelowWaist) {
                return first;
            }
            if (lastBelowWaist && !firstBelowWaist) {
                return last;
            }
            // Both satisfy or neither satisfies: pick smaller y
            return (firstY < lastY) ? first : last;
        } else {
            // BOTTOM mode: prefer y > waistY, else larger y
            boolean firstAboveWaist = firstY > waistY;
            boolean lastAboveWaist = lastY > waistY;
            
            if (firstAboveWaist && !lastAboveWaist) {
                return first;
            }
            if (lastAboveWaist && !firstAboveWaist) {
                return last;
            }
            // Both satisfy or neither satisfies: pick larger y
            return (firstY > lastY) ? first : last;
        }
    }

    /**
     * Pick point from curve by extreme X (minX if left=true, else maxX).
     */
    private Pt extremeByX(Curve2D c, boolean left) {
        if (c == null) {
            return null;
        }

        List<Pt> pts = c.getPoints();
        if (pts == null || pts.isEmpty()) {
            return null;
        }

        Pt best = null;
        double bestX = left ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY;

        for (Pt p : pts) {
            if (p == null) {
                continue;
            }

            double x = p.getX();
            if (!Double.isFinite(x)) {
                continue;
            }

            if (left) {
                if (x < bestX) {
                    bestX = x;
                    best = p;
                }
            } else {
                if (x > bestX) {
                    bestX = x;
                    best = p;
                }
            }
        }

        return best;
    }
}
