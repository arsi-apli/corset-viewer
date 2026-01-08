package sk.arsi.corset.util;

import sk.arsi.corset.model.Curve2D;
import sk.arsi.corset.model.PanelCurves;
import sk.arsi.corset.model.Pt;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility for computing seam allowance offset curves. Ensures offset is always
 * OUTSIDE the panel using geometric tests.
 */
public final class SeamAllowanceComputer {

    // Geometric test parameters
    private static final double EPSILON = 1e-9; // Tolerance for floating-point comparisons

    // For polyline cleanup (remove consecutive points closer than this)
    // Needs to be larger than EPSILON, but still "tiny" in mm scale.
    private static final double MIN_SEGMENT_LEN = 1e-4; // 0.0001 mm (safe default)

    // Majority test count for sign detection (odd number)
    private static final int SIGN_TEST_SAMPLES = 7;

    private SeamAllowanceComputer() {
        // utility class
    }

    /**
     * Determine if a seam ID should have allowance generated. Only internal
     * vertical seams (AB, BC, CD, DE, EF and their UP/DOWN variants). Exclude
     * outer seams AA_* and FF_*.
     */
    public static boolean shouldGenerateAllowance(String seamId) {
        if (seamId == null) {
            return false;
        }
        return seamId.startsWith("AB_") || seamId.startsWith("BA_")
                || seamId.startsWith("BC_") || seamId.startsWith("CB_")
                || seamId.startsWith("CD_") || seamId.startsWith("DC_")
                || seamId.startsWith("DE_") || seamId.startsWith("ED_")
                || seamId.startsWith("EF_") || seamId.startsWith("FE_");
    }

    /**
     * Compute offset curve for a seam, offsetting OUTSIDE the panel. Uses
     * geometric test to determine correct offset direction.
     *
     * @param seamCurve The seam curve to offset
     * @param panel The panel that owns this seam
     * @param offsetDistance Offset distance (positive value, will be applied
     * outward)
     * @return Offset curve points, or null if computation fails
     */
    public static List<Pt> computeOffsetCurve(Curve2D seamCurve, PanelCurves panel, double offsetDistance) {
        if (seamCurve == null || panel == null || offsetDistance <= 0) {
            return null;
        }

        List<Pt> rawPoints = seamCurve.getPoints();
        if (rawPoints == null || rawPoints.size() < 2) {
            return null;
        }

        // Clean up points to avoid zero-length segments / degenerate normals after resize/resampling.
        List<Pt> points = cleanupPolyline(rawPoints);
        if (points.size() < 2) {
            return null;
        }

        // Compute panel interior reference point (centroid of waist)
        Pt interiorRef = computePanelInterior(panel);
        if (interiorRef == null) {
            return null;
        }

        // Determine offset direction using robust multi-sample test
        int offsetSign = determineOffsetSign(points, interiorRef, offsetDistance);

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
     * Remove consecutive duplicate / near-duplicate points which create
     * degenerate tangents.
     */
    private static List<Pt> cleanupPolyline(List<Pt> points) {
        List<Pt> out = new ArrayList<>(points.size());
        Pt prev = null;

        double minSeg2 = MIN_SEGMENT_LEN * MIN_SEGMENT_LEN;

        for (Pt p : points) {
            if (p == null || !Double.isFinite(p.getX()) || !Double.isFinite(p.getY())) {
                continue;
            }
            if (prev == null) {
                out.add(p);
                prev = p;
                continue;
            }

            double dx = p.getX() - prev.getX();
            double dy = p.getY() - prev.getY();
            if (dx * dx + dy * dy < minSeg2) {
                // skip tiny step
                continue;
            }

            out.add(p);
            prev = p;
        }

        // If we removed too much and ended with 1 point, try to keep last original finite point.
        if (out.size() < 2) {
            // fallback: keep first two finite points from original list
            out.clear();
            for (Pt p : points) {
                if (p != null && Double.isFinite(p.getX()) && Double.isFinite(p.getY())) {
                    out.add(p);
                    if (out.size() == 2) {
                        break;
                    }
                }
            }
        }

        return out;
    }

    /**
     * Determine offset sign: +1 if normal points away from interior, -1 if
     * toward interior (then we flip).
     *
     * Robust version: - tests several segments around the middle and uses
     * majority vote - uses adaptive test distance based on offsetDistance
     */
    private static int determineOffsetSign(List<Pt> points, Pt interiorRef, double offsetDistance) {
        int n = points.size();
        if (n < 2) {
            return 1;
        }

        // Adaptive test distance:
        // - if offsetDistance is small, 1mm can dominate; if huge, 1mm too small
        // Clamp to reasonable range.
        double testDist = clamp(Math.abs(offsetDistance), 0.5, 5.0);

        int mid = n / 2;

        // choose sample start so we stay within [0, n-2]
        int half = SIGN_TEST_SAMPLES / 2;
        int start = Math.max(0, Math.min((n - 2) - (SIGN_TEST_SAMPLES - 1), mid - half));

        int votesPlus = 0;
        int votesMinus = 0;

        for (int k = 0; k < SIGN_TEST_SAMPLES; k++) {
            int idx = start + k;
            if (idx < 0 || idx >= n - 1) {
                continue;
            }

            Pt p0 = points.get(idx);
            Pt p1 = points.get(idx + 1);
            if (p0 == null || p1 == null) {
                continue;
            }

            double dx = p1.getX() - p0.getX();
            double dy = p1.getY() - p0.getY();
            double len2 = dx * dx + dy * dy;
            if (len2 < EPSILON) {
                continue;
            }

            // unit normal (+n)
            double nx = -dy;
            double ny = dx;
            double nLen = Math.sqrt(nx * nx + ny * ny);
            if (nLen < EPSILON) {
                continue;
            }
            nx /= nLen;
            ny /= nLen;

            // Midpoint
            double mx = (p0.getX() + p1.getX()) * 0.5;
            double my = (p0.getY() + p1.getY()) * 0.5;

            double xPlus = mx + nx * testDist;
            double yPlus = my + ny * testDist;

            double xMinus = mx - nx * testDist;
            double yMinus = my - ny * testDist;

            double distPlus = distanceSquared(xPlus, yPlus, interiorRef.getX(), interiorRef.getY());
            double distMinus = distanceSquared(xMinus, yMinus, interiorRef.getX(), interiorRef.getY());

            if (distPlus > distMinus) {
                votesPlus++;
            } else {
                votesMinus++;
            }
        }

        // If we couldn't vote reliably, fallback to original mid-segment method
        if (votesPlus == 0 && votesMinus == 0) {
            return determineOffsetSignFallback(points, interiorRef, testDist);
        }

        return (votesPlus >= votesMinus) ? 1 : -1;
    }

    private static int determineOffsetSignFallback(List<Pt> points, Pt interiorRef, double testDist) {
        int n = points.size();
        int midIdx = n / 2;
        if (midIdx >= n - 1) {
            midIdx = n - 2;
        }

        Pt p0 = points.get(midIdx);
        Pt p1 = points.get(midIdx + 1);
        if (p0 == null || p1 == null) {
            return 1;
        }

        double dx = p1.getX() - p0.getX();
        double dy = p1.getY() - p0.getY();

        double nx = -dy;
        double ny = dx;

        double nLen = Math.sqrt(nx * nx + ny * ny);
        if (nLen < EPSILON) {
            return 1;
        }
        nx /= nLen;
        ny /= nLen;

        double mx = (p0.getX() + p1.getX()) * 0.5;
        double my = (p0.getY() + p1.getY()) * 0.5;

        double distPlus = distanceSquared(mx + nx * testDist, my + ny * testDist, interiorRef.getX(), interiorRef.getY());
        double distMinus = distanceSquared(mx - nx * testDist, my - ny * testDist, interiorRef.getX(), interiorRef.getY());

        return (distPlus > distMinus) ? 1 : -1;
    }

    private static double distanceSquared(double x1, double y1, double x2, double y2) {
        double dx = x1 - x2;
        double dy = y1 - y2;
        return dx * dx + dy * dy;
    }

    /**
     * Generate offset polyline by offsetting each vertex perpendicular to local
     * tangent. Simple per-vertex offset (not a true mathematical offset curve,
     * but sufficient for visualization).
     *
     * Improvements: - if a vertex has degenerate tangent, reuse previous valid
     * normal or search nearby segment - avoid leaving points unoffset (which
     * creates visible "jumps")
     */
    private static List<Pt> generateOffsetPolyline(List<Pt> points, double offsetDist) {
        int n = points.size();
        List<Pt> result = new ArrayList<>(n);

        double prevNx = 0.0;
        double prevNy = 0.0;
        boolean hasPrevNormal = false;

        for (int i = 0; i < n; i++) {
            Pt p = points.get(i);
            if (p == null) {
                continue;
            }

            // Try compute local tangent using neighbors (same strategy as before)
            double tx = 0.0, ty = 0.0;
            boolean ok = false;

            if (i == 0) {
                Pt pNext = points.get(1);
                if (pNext != null) {
                    tx = pNext.getX() - p.getX();
                    ty = pNext.getY() - p.getY();
                    ok = (tx * tx + ty * ty) > EPSILON;
                }
            } else if (i == n - 1) {
                Pt pPrev = points.get(i - 1);
                if (pPrev != null) {
                    tx = p.getX() - pPrev.getX();
                    ty = p.getY() - pPrev.getY();
                    ok = (tx * tx + ty * ty) > EPSILON;
                }
            } else {
                Pt pPrev = points.get(i - 1);
                Pt pNext = points.get(i + 1);
                if (pPrev != null && pNext != null) {
                    tx = pNext.getX() - pPrev.getX();
                    ty = pNext.getY() - pPrev.getY();
                    ok = (tx * tx + ty * ty) > EPSILON;
                }
            }

            double nx, ny;
            if (ok) {
                nx = -ty;
                ny = tx;
                double nLen = Math.sqrt(nx * nx + ny * ny);
                if (nLen > EPSILON) {
                    nx /= nLen;
                    ny /= nLen;
                } else {
                    ok = false;
                    nx = 0.0;
                    ny = 0.0;
                }
            } else {
                nx = 0.0;
                ny = 0.0;
            }

            // Fallbacks for degenerate normals:
            if (!ok) {
                if (hasPrevNormal) {
                    nx = prevNx;
                    ny = prevNy;
                    ok = true;
                } else {
                    // search for a nearby valid segment
                    double[] found = findAnyValidUnitNormal(points, i);
                    if (found != null) {
                        nx = found[0];
                        ny = found[1];
                        ok = true;
                    }
                }
            }

            if (!ok) {
                // As a last resort, keep point as-is (should be rare after cleanup)
                result.add(p);
                continue;
            }

            // Keep normal orientation consistent along curve (avoid sudden flips)
            if (hasPrevNormal) {
                double dot = nx * prevNx + ny * prevNy;
                if (dot < 0.0) {
                    nx = -nx;
                    ny = -ny;
                }
            }
            prevNx = nx;
            prevNy = ny;
            hasPrevNormal = true;

            double ox = p.getX() + nx * offsetDist;
            double oy = p.getY() + ny * offsetDist;
            result.add(new Pt(ox, oy));
        }

        return result;
    }

    /**
     * Find any valid unit normal near index i by scanning outward for a
     * non-degenerate segment.
     */
    private static double[] findAnyValidUnitNormal(List<Pt> points, int i) {
        int n = points.size();

        for (int radius = 1; radius < n; radius++) {
            int left = i - radius;
            int right = i + radius;

            // try segment (left, left+1)
            if (left >= 0 && left + 1 < n) {
                double[] nrm = unitNormalFromSegment(points.get(left), points.get(left + 1));
                if (nrm != null) {
                    return nrm;
                }
            }
            // try segment (right-1, right)
            if (right - 1 >= 0 && right < n) {
                double[] nrm = unitNormalFromSegment(points.get(right - 1), points.get(right));
                if (nrm != null) {
                    return nrm;
                }
            }
        }
        return null;
    }

    private static double[] unitNormalFromSegment(Pt a, Pt b) {
        if (a == null || b == null) {
            return null;
        }
        double dx = b.getX() - a.getX();
        double dy = b.getY() - a.getY();
        double len2 = dx * dx + dy * dy;
        if (len2 < EPSILON) {
            return null;
        }
        double nx = -dy;
        double ny = dx;
        double nLen = Math.sqrt(nx * nx + ny * ny);
        if (nLen < EPSILON) {
            return null;
        }
        return new double[]{nx / nLen, ny / nLen};
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
