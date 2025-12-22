package sk.arsi.corset.layout;

import sk.arsi.corset.model.Curve2D;
import sk.arsi.corset.model.PanelCurves;
import sk.arsi.corset.model.Pt;

import java.util.ArrayList;
import java.util.List;

/**
 * Computes chained panel layout for pseudo-3D visualization.
 * Chains panels along TOP or BOTTOM edge with rotation and translation.
 */
public final class ChainLayoutEngine {

    public enum EdgeMode {
        TOP,
        BOTTOM
    }

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
                double tx = (prevJointWorld != null) ? prevJointWorld.getX() + 150.0 : 0.0;
                double ty = (prevJointWorld != null) ? prevJointWorld.getY() : 0.0;
                Transform2D transform = new Transform2D(0.0, 0.0, 0.0, tx, ty);
                results.add(new LayoutResult(panel, transform));
                
                if (waistRight != null) {
                    prevJointWorld = transform.apply(waistRight);
                } else {
                    prevJointWorld = new Pt(tx + 150.0, ty);
                }
                prevEdgeRightWorld = null;
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

                // Update world positions
                prevJointWorld = transform.apply(waistRight);
                prevEdgeRightWorld = transform.apply(edgeRight);
            }

            results.add(new LayoutResult(panel, transform));
        }

        return results;
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
