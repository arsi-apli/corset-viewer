package sk.arsi.corset.geom;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SeamUtils {

    private SeamUtils() {
    }

    public static final class Pt2 {

        public final double x;
        public final double y;

        public Pt2(double x, double y) {
            this.x = x;
            this.y = y;
        }

        public Pt2 minus(Pt2 o) {
            return new Pt2(this.x - o.x, this.y - o.y);
        }

        public double len() {
            return Math.sqrt(x * x + y * y);
        }
    }

    public static final class Intersection {

        public final Pt2 p;
        public final double param; // i + t (segment index + interpolation)

        public Intersection(Pt2 p, double param) {
            this.p = p;
            this.param = param;
        }
    }

    /**
     * Returns a new list reversed (does not mutate input).
     */
    public static List<Pt2> reversedCopy(List<Pt2> pts) {
        List<Pt2> out = new ArrayList<Pt2>(pts);
        Collections.reverse(out);
        return out;
    }

    /**
     * Distance in Y only (for "closest to waist line").
     */
    private static double dyAbs(Pt2 p, double y) {
        return Math.abs(p.y - y);
    }

    /**
     * Build a continuous seam polyline from *_UP and *_DOWN: - UP ends at waist
     * - DOWN starts at waist - returned polyline has a known "waist junction
     * param" = up.size()-1
     */
    public static SeamPolyline buildSeamPolyline(List<Pt2> up, List<Pt2> down, double waistY) {
        if (up == null || up.size() < 2) {
            throw new IllegalArgumentException("UP curve missing or too short.");
        }
        if (down == null || down.size() < 2) {
            throw new IllegalArgumentException("DOWN curve missing or too short.");
        }

        List<Pt2> upOriented = orientUpToEndAtWaist(up, waistY);
        List<Pt2> downOriented = orientDownToStartAtWaist(down, waistY);

        int junctionIndex = upOriented.size() - 1;

        List<Pt2> seam = new ArrayList<Pt2>(upOriented.size() + downOriented.size());
        seam.addAll(upOriented);

        // avoid duplicating the waist point if same (or near-same)
        Pt2 lastUp = upOriented.get(upOriented.size() - 1);
        Pt2 firstDown = downOriented.get(0);
        double eps = 1e-6;
        boolean same = Math.abs(lastUp.x - firstDown.x) < eps && Math.abs(lastUp.y - firstDown.y) < eps;

        if (same) {
            seam.addAll(downOriented.subList(1, downOriented.size()));
        } else {
            seam.addAll(downOriented);
        }

        return new SeamPolyline(seam, junctionIndex);
    }

    private static List<Pt2> orientUpToEndAtWaist(List<Pt2> up, double waistY) {
        Pt2 a = up.get(0);
        Pt2 b = up.get(up.size() - 1);

        // endpoint closer to waist should be the END
        if (dyAbs(b, waistY) <= dyAbs(a, waistY)) {
            return up;
        }
        return reversedCopy(up);
    }

    private static List<Pt2> orientDownToStartAtWaist(List<Pt2> down, double waistY) {
        Pt2 a = down.get(0);
        Pt2 b = down.get(down.size() - 1);

        // endpoint closer to waist should be the START
        if (dyAbs(a, waistY) <= dyAbs(b, waistY)) {
            return down;
        }
        return reversedCopy(down);
    }

    public static final class SeamPolyline {

        public final List<Pt2> pts;
        public final double waistParam; // junction at waist (segment param space)

        public SeamPolyline(List<Pt2> pts, int waistJunctionIndex) {
            this.pts = pts;
            this.waistParam = (double) waistJunctionIndex;
        }
    }

    /**
     * Find intersections of a polyline with horizontal line y=targetY. Returns
     * all candidates (can be 0,1,2,...) with a "param" value.
     */
    public static List<Intersection> intersectHorizontal(List<Pt2> polyline, double targetY) {
        List<Intersection> out = new ArrayList<Intersection>();

        for (int i = 0; i < polyline.size() - 1; i++) {
            Pt2 p0 = polyline.get(i);
            Pt2 p1 = polyline.get(i + 1);

            double y0 = p0.y;
            double y1 = p1.y;

            // skip horizontal segments (or nearly)
            double dy = y1 - y0;
            if (Math.abs(dy) < 1e-12) {
                continue;
            }

            boolean crosses = (targetY >= Math.min(y0, y1)) && (targetY <= Math.max(y0, y1));
            if (!crosses) {
                continue;
            }

            double t = (targetY - y0) / dy; // 0..1
            if (t < -1e-9 || t > 1.0 + 1e-9) {
                continue;
            }

            double x = p0.x + t * (p1.x - p0.x);
            Pt2 ip = new Pt2(x, targetY);
            double param = (double) i + t;

            out.add(new Intersection(ip, param));
        }

        return out;
    }

    /**
     * Pick the "best" intersection: the one closest (in param space) to seam
     * waist junction. This makes it robust when a seam wiggles and crosses a
     * horizontal line more than once.
     */
    public static Pt2 pickClosestToWaist(List<Intersection> candidates, double waistParam) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        Intersection best = candidates.get(0);
        double bestDist = Math.abs(best.param - waistParam);

        for (int i = 1; i < candidates.size(); i++) {
            Intersection c = candidates.get(i);
            double d = Math.abs(c.param - waistParam);
            if (d < bestDist) {
                best = c;
                bestDist = d;
            }
        }
        return best.p;
    }
}
