package sk.arsi.corset.util;

import sk.arsi.corset.model.Curve2D;
import sk.arsi.corset.model.PanelCurves;
import sk.arsi.corset.model.PanelId;
import sk.arsi.corset.model.Pt;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility for computing seam allowance offset curves.
 * Ensures offset is always OUTSIDE the panel using geometric tests.
 */
public final class SeamAllowanceComputer {

    private SeamAllowanceComputer() {
        // utility class
    }

    /**
     * Determine if a seam ID should have allowance generated.
     * Only internal vertical seams (AB, BC, CD, DE, EF and their UP/DOWN variants).
     * Exclude outer seams AA_* and FF_*.
     */
    public static boolean shouldGenerateAllowance(String seamId) {
        if (seamId == null) {
            return false;
        }
        // Include AB, BC, CD, DE, EF and also BA, CB, DC, ED, FE (bidirectional)
        // Exclude AA and FF (outer edges)
        return seamId.startsWith("AB_") || seamId.startsWith("BA_")
                || seamId.startsWith("BC_") || seamId.startsWith("CB_")
                || seamId.startsWith("CD_") || seamId.startsWith("DC_")
                || seamId.startsWith("DE_") || seamId.startsWith("ED_")
                || seamId.startsWith("EF_") || seamId.startsWith("FE_");
    }

    /**
     * Compute offset curve for a seam, offsetting OUTSIDE the panel.
     * Uses geometric test to determine correct offset direction.
     *
     * @param seamCurve The seam curve to offset
     * @param panel The panel that owns this seam
     * @param offsetDistance Offset distance (positive value, will be applied outward)
     * @return Offset curve points, or null if computation fails
     */
    public static List<Pt> computeOffsetCurve(Curve2D seamCurve, PanelCurves panel, double offsetDistance) {
        if (seamCurve == null || panel == null || offsetDistance <= 0) {
            return null;
        }

        List<Pt> points = seamCurve.getPoints();
        if (points == null || points.size() < 2) {
            return null;
        }

        // Compute panel interior reference point (centroid of waist)
        Pt interiorRef = computePanelInterior(panel);
        if (interiorRef == null) {
            return null;
        }

        // Determine offset direction using mid-point test
        int offsetSign = determineOffsetSign(points, interiorRef);

        // Generate offset polyline
        return generateOffsetPolyline(points, offsetDistance * offsetSign);
    }

    /**
     * Compute panel interior reference point as centroid of waist curve.
     */
    private static Pt computePanelInterior(PanelCurves panel) {
        Curve2D waist = panel.getWaist();
        if (waist == null || waist.getPoints() == null || waist.getPoints().isEmpty()) {
            return null;
        }

        List<Pt> waistPts = waist.getPoints();
        double sumX = 0.0;
        double sumY = 0.0;
        int count = 0;

        for (Pt p : waistPts) {
            if (p != null && Double.isFinite(p.getX()) && Double.isFinite(p.getY())) {
                sumX += p.getX();
                sumY += p.getY();
                count++;
            }
        }

        if (count == 0) {
            return null;
        }

        return new Pt(sumX / count, sumY / count);
    }

    /**
     * Determine offset sign: +1 if normal points away from interior, -1 if toward interior.
     * Test at mid-parameter of the curve.
     */
    private static int determineOffsetSign(List<Pt> points, Pt interiorRef) {
        int n = points.size();
        if (n < 2) {
            return 1; // fallback
        }

        // Use mid-segment
        int midIdx = n / 2;
        if (midIdx >= n - 1) {
            midIdx = n - 2;
        }

        Pt p0 = points.get(midIdx);
        Pt p1 = points.get(midIdx + 1);

        if (p0 == null || p1 == null) {
            return 1; // fallback
        }

        // Compute tangent and normal at mid-segment
        double dx = p1.getX() - p0.getX();
        double dy = p1.getY() - p0.getY();

        // Normal (perpendicular to tangent): rotate tangent by 90 degrees
        double nx = -dy;
        double ny = dx;

        // Normalize normal
        double nLen = Math.sqrt(nx * nx + ny * ny);
        if (nLen < 1e-9) {
            return 1; // degenerate segment
        }
        nx /= nLen;
        ny /= nLen;

        // Mid-point of segment
        double mx = (p0.getX() + p1.getX()) / 2.0;
        double my = (p0.getY() + p1.getY()) / 2.0;

        // Test two points: pMid + n*eps and pMid - n*eps
        double eps = 1.0; // test distance

        double xPlus = mx + nx * eps;
        double yPlus = my + ny * eps;

        double xMinus = mx - nx * eps;
        double yMinus = my - ny * eps;

        // Distance to interior reference
        double distPlus = distanceSquared(xPlus, yPlus, interiorRef.getX(), interiorRef.getY());
        double distMinus = distanceSquared(xMinus, yMinus, interiorRef.getX(), interiorRef.getY());

        // If +n direction is farther from interior, offset in +n direction
        // If -n direction is farther from interior, offset in -n direction
        if (distPlus > distMinus) {
            return 1; // offset in +n direction (away from interior)
        } else {
            return -1; // offset in -n direction (away from interior)
        }
    }

    private static double distanceSquared(double x1, double y1, double x2, double y2) {
        double dx = x1 - x2;
        double dy = y1 - y2;
        return dx * dx + dy * dy;
    }

    /**
     * Generate offset polyline by offsetting each vertex perpendicular to local tangent.
     * Simple per-vertex offset (not a true mathematical offset curve, but sufficient for visualization).
     */
    private static List<Pt> generateOffsetPolyline(List<Pt> points, double offsetDist) {
        int n = points.size();
        List<Pt> result = new ArrayList<>(n);

        for (int i = 0; i < n; i++) {
            Pt p = points.get(i);
            if (p == null) {
                continue;
            }

            // Compute local tangent using neighbors
            double tx, ty;

            if (i == 0) {
                // First point: use forward difference
                if (n < 2 || points.get(1) == null) {
                    result.add(p);
                    continue;
                }
                Pt pNext = points.get(1);
                tx = pNext.getX() - p.getX();
                ty = pNext.getY() - p.getY();
            } else if (i == n - 1) {
                // Last point: use backward difference
                Pt pPrev = points.get(i - 1);
                if (pPrev == null) {
                    result.add(p);
                    continue;
                }
                tx = p.getX() - pPrev.getX();
                ty = p.getY() - pPrev.getY();
            } else {
                // Middle point: use central difference
                Pt pPrev = points.get(i - 1);
                Pt pNext = points.get(i + 1);
                if (pPrev == null || pNext == null) {
                    result.add(p);
                    continue;
                }
                tx = pNext.getX() - pPrev.getX();
                ty = pNext.getY() - pPrev.getY();
            }

            // Normal (perpendicular)
            double nx = -ty;
            double ny = tx;

            // Normalize
            double nLen = Math.sqrt(nx * nx + ny * ny);
            if (nLen < 1e-9) {
                result.add(p);
                continue;
            }
            nx /= nLen;
            ny /= nLen;

            // Offset point
            double ox = p.getX() + nx * offsetDist;
            double oy = p.getY() + ny * offsetDist;

            result.add(new Pt(ox, oy));
        }

        return result;
    }
}
