package sk.arsi.corset.measure;

import sk.arsi.corset.jts.SvgPathToJts;
import sk.arsi.corset.model.Curve2D;
import sk.arsi.corset.model.PanelCurves;
import sk.arsi.corset.model.Pt;
import sk.arsi.corset.resize.SvgPathEditor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.OptionalDouble;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Measurement utilities with analytic SVG path intersection.
 *
 * This version uses SvgPathEditor.normalizePath(...) to normalize path 'd'
 * strings (absolute coordinates, implicit L -> explicit L) before any analytic
 * or JTS-based processing. That prevents implicit-L truncation issues and
 * improves stability near the waist.
 *
 * Supported analytic segment types: M/m (with implicit L), L/l, Q/q, C/c, Z/z.
 *
 * Fallback: if analytic solver or JTS fails, falls back to polyline sampling
 * using Curve2D.points.
 */
public final class MeasurementUtils {

    private MeasurementUtils() {
    }

    // Numerical tolerance
    private static final double EPS = 1e-9;

    // Dead-zone around waist to use waist curve length
    private static final double DEAD_ZONE_MM = 0.1;

    private static final double MAX_DY_SEARCH_DISTANCE = 1000.0;
    private static final double MIN_STEP_SIZE = 0.5;

    // flatness and tolerances for JTS fallback when used
    private static final double FLATNESS_MM = 0.5;
    private static final double JTS_NEAREST_TOLERANCE_MM = 0.5;

    public enum SeamSide {
        TO_PREV, TO_NEXT
    }

    public static final class SeamSplit {

        public final double above;
        public final double below;

        public SeamSplit(double above, double below) {
            this.above = above;
            this.below = below;
        }
    }

    // -------------------- Waist reference --------------------
    public static double computePanelWaistY0(Curve2D waist) {
        if (waist == null || waist.getPoints() == null || waist.getPoints().isEmpty()) {
            return 0.0;
        }
        List<Double> yValues = new ArrayList<>();
        for (Pt p : waist.getPoints()) {
            if (p != null && Double.isFinite(p.getY())) {
                yValues.add(p.getY());
            }
        }
        if (yValues.isEmpty()) {
            return 0.0;
        }
        Collections.sort(yValues);
        int n = yValues.size();
        if (n % 2 == 1) {
            return yValues.get(n / 2);
        }
        return (yValues.get(n / 2 - 1) + yValues.get(n / 2)) / 2.0;
    }

    // -------------------- Curve length --------------------
    public static double computeCurveLength(Curve2D curve) {
        if (curve == null) {
            return 0.0;
        }
        String d = curve.getD();
        if (d != null && !d.trim().isEmpty()) {
            try {
                String norm = SvgPathEditor.normalizePath(d);
                return SvgPathToJts.pathDToGeometry(norm, FLATNESS_MM).getLength();
            } catch (Throwable t) {
                // fall through to polyline-based length
            }
        }
        if (curve.getPoints() == null || curve.getPoints().size() < 2) {
            return 0.0;
        }
        double sum = 0.0;
        List<Pt> pts = curve.getPoints();
        for (int i = 0; i < pts.size() - 1; ++i) {
            Pt a = pts.get(i);
            Pt b = pts.get(i + 1);
            if (a == null || b == null) {
                continue;
            }
            double dx = b.getX() - a.getX();
            double dy = b.getY() - a.getY();
            sum += Math.hypot(dx, dy);
        }
        return sum;
    }

    // -------------------- Curve length portion --------------------
    public static double computeCurveLengthPortion(Curve2D curve, double waistY, boolean above) {
        if (curve == null) {
            return 0.0;
        }

        // Prefer JTS-based calculation from normalized 'd' if available
        String d = curve.getD();
        if (d != null && !d.trim().isEmpty()) {
            try {
                String norm = SvgPathEditor.normalizePath(d);
                double[] extent = computeCurveXExtent(curve);
                double minX = extent[0] - 1000.0;
                double maxX = extent[1] + 1000.0;
                return SvgPathToJts.lengthOfCurveInHalfPlane(norm, waistY, above, minX, maxX, FLATNESS_MM);
            } catch (Throwable t) {
                // fallback to sampled method below
            }
        }

        // Fallback: sample segments and compute portion
        if (curve.getPoints() == null || curve.getPoints().size() < 2) {
            return 0.0;
        }
        List<Pt> pts = curve.getPoints();
        double length = 0.0;
        for (int i = 0; i < pts.size() - 1; ++i) {
            Pt p0 = pts.get(i), p1 = pts.get(i + 1);
            if (p0 == null || p1 == null) {
                continue;
            }
            double x0 = p0.getX(), y0 = p0.getY();
            double x1 = p1.getX(), y1 = p1.getY();
            if (!Double.isFinite(x0) || !Double.isFinite(y0) || !Double.isFinite(x1) || !Double.isFinite(y1)) {
                continue;
            }
            boolean a0 = y0 < waistY;
            boolean a1 = y1 < waistY;
            if (a0 == a1) {
                if (a0 == above) {
                    length += Math.hypot(x1 - x0, y1 - y0);
                }
            } else {
                double t = (waistY - y0) / (y1 - y0);
                double xs = x0 + t * (x1 - x0);
                if (a0 == above) {
                    length += Math.hypot(xs - x0, waistY - y0);
                } else {
                    length += Math.hypot(x1 - xs, y1 - waistY);
                }
            }
        }
        return length;
    }

    private static double[] computeCurveXExtent(Curve2D curve) {
        if (curve == null || curve.getPoints() == null || curve.getPoints().isEmpty()) {
            return new double[]{-1e6, 1e6};
        }
        double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
        for (Pt p : curve.getPoints()) {
            if (p == null) {
                continue;
            }
            double x = p.getX();
            if (!Double.isFinite(x)) {
                continue;
            }
            min = Math.min(min, x);
            max = Math.max(max, x);
        }
        if (min == Double.POSITIVE_INFINITY) {
            return new double[]{-1e6, 1e6};
        }
        return new double[]{min - 1.0, max + 1.0};
    }

    private static Curve2D pickSeamCurve(PanelCurves p, SeamSide side, boolean upCurve) {
        if (p == null) {
            return null;
        }
        if (side == SeamSide.TO_NEXT) {
            return upCurve ? p.getSeamToNextUp() : p.getSeamToNextDown();
        }
        return upCurve ? p.getSeamToPrevUp() : p.getSeamToPrevDown();
    }

    public static SeamSplit measureSeamSplitAtWaist(PanelCurves panel, SeamSide side, boolean upCurve) {
        if (panel == null) {
            return new SeamSplit(0.0, 0.0);
        }
        double waistY = computePanelWaistY0(panel.getWaist());
        Curve2D seam = pickSeamCurve(panel, side, upCurve);
        double above = computeCurveLengthPortion(seam, waistY, true);
        double below = computeCurveLengthPortion(seam, waistY, false);
        return new SeamSplit(above, below);
    }

    // -------------------- Analytic intersection from 'd' --------------------
    // Lightweight parser for analytic intersection
    private static final Pattern CMD_PATTERN = Pattern.compile("([MmLlQqCcZz])([^MmLlQqCcZz]*)");

    private static class Cmd {

        final char type;
        final double[] coords;

        Cmd(char type, double[] coords) {
            this.type = type;
            this.coords = coords;
        }
    }

    private static List<Cmd> parsePath(String d) {
        List<Cmd> out = new ArrayList<>();
        if (d == null || d.trim().isEmpty()) {
            return out;
        }
        Matcher m = CMD_PATTERN.matcher(d);
        while (m.find()) {
            char cmd = m.group(1).charAt(0);
            String coordsStr = m.group(2).trim();
            double[] coords = parseCoords(coordsStr);
            out.add(new Cmd(cmd, coords));
        }
        return out;
    }

    private static double[] parseCoords(String coordsStr) {
        if (coordsStr == null || coordsStr.trim().isEmpty()) {
            return new double[0];
        }
        String[] parts = coordsStr.trim().split("[,\\s]+");
        List<Double> vals = new ArrayList<>();
        for (String p : parts) {
            if (p.isEmpty()) {
                continue;
            }
            try {
                vals.add(Double.parseDouble(p));
            } catch (NumberFormatException ex) {
                // ignore invalid token
            }
        }
        double[] arr = new double[vals.size()];
        for (int i = 0; i < vals.size(); ++i) {
            arr[i] = vals.get(i);
        }
        return arr;
    }

    /**
     * Analytically compute X intersections for path string d with horizontal
     * line y. Returns sorted X coordinates. Uses SvgPathEditor.normalizePath to
     * ensure the 'd' string uses absolute commands and explicit L segments.
     */
    public static List<Double> intersectHorizontalXsFromPathDAnalytic(String d, double y) {
        List<Double> xs = new ArrayList<>();
        if (d == null || d.trim().isEmpty()) {
            return xs;
        }

        String norm;
        try {
            norm = SvgPathEditor.normalizePath(d);
        } catch (Throwable t) {
            // fallback: use original d
            norm = d;
        }

        List<Cmd> cmds = parsePath(norm);
        double curX = 0.0, curY = 0.0;
        double startX = Double.NaN, startY = Double.NaN;
        boolean haveCurrent = false;

        for (Cmd cmd : cmds) {
            char type = cmd.type;
            double[] c = cmd.coords;

            switch (type) {
                case 'M': {
                    int idx = 0;
                    while (idx + 1 < c.length) {
                        double nx = c[idx], ny = c[idx + 1];
                        if (!haveCurrent) {
                            curX = nx;
                            curY = ny;
                            startX = curX;
                            startY = curY;
                            haveCurrent = true;
                        } else {
                            processLineIntersections(curX, curY, nx, ny, y, xs);
                            curX = nx;
                            curY = ny;
                        }
                        idx += 2;
                    }
                    break;
                }

                case 'L': {
                    int idx = 0;
                    while (idx + 1 < c.length) {
                        double nx = c[idx], ny = c[idx + 1];
                        if (!haveCurrent) {
                            curX = nx;
                            curY = ny;
                            startX = curX;
                            startY = curY;
                            haveCurrent = true;
                        } else {
                            processLineIntersections(curX, curY, nx, ny, y, xs);
                            curX = nx;
                            curY = ny;
                        }
                        idx += 2;
                    }
                    break;
                }

                case 'Q': {
                    int idx = 0;
                    while (idx + 3 < c.length) {
                        double x1 = c[idx], y1 = c[idx + 1], x2 = c[idx + 2], y2 = c[idx + 3];
                        if (!haveCurrent) {
                            curX = x2;
                            curY = y2;
                            startX = curX;
                            startY = curY;
                            haveCurrent = true;
                        } else {
                            processQuadraticIntersections(curX, curY, x1, y1, x2, y2, y, xs);
                            curX = x2;
                            curY = y2;
                        }
                        idx += 4;
                    }
                    break;
                }

                case 'C': {
                    int idx = 0;
                    while (idx + 5 < c.length) {
                        double x1 = c[idx], y1 = c[idx + 1],
                                x2 = c[idx + 2], y2 = c[idx + 3],
                                x3 = c[idx + 4], y3 = c[idx + 5];
                        if (!haveCurrent) {
                            curX = x3;
                            curY = y3;
                            startX = curX;
                            startY = curY;
                            haveCurrent = true;
                        } else {
                            processCubicIntersections(curX, curY, x1, y1, x2, y2, x3, y3, y, xs);
                            curX = x3;
                            curY = y3;
                        }
                        idx += 6;
                    }
                    break;
                }

                case 'Z': {
                    if (haveCurrent && !Double.isNaN(startX)) {
                        processLineIntersections(curX, curY, startX, startY, y, xs);
                        curX = startX;
                        curY = startY;
                    }
                    break;
                }

                default:
                    // unsupported commands are ignored (normalize should have removed most)
                    break;
            }
        }

        Collections.sort(xs);
        return xs;
    }

    // -------------------- segment intersection helpers --------------------
    private static void processLineIntersections(double x0, double y0, double x1, double y1,
            double y, List<Double> out) {
        if (!Double.isFinite(y0) || !Double.isFinite(y1)) {
            return;
        }
        if (Math.abs(y1 - y0) < EPS) {
            return;
        }
        double t = (y - y0) / (y1 - y0);
        if (t >= -1e-12 && t <= 1.0 + 1e-12) {
            double x = x0 + t * (x1 - x0);
            if (Double.isFinite(x)) {
                out.add(x);
            }
        }
    }

    private static void processQuadraticIntersections(double x0, double y0,
            double x1, double y1,
            double x2, double y2,
            double y, List<Double> out) {
        double ay = y0 - 2 * y1 + y2;
        double by = -2 * y0 + 2 * y1;
        double cy = y0 - y;
        double[] roots = solveQuadratic(ay, by, cy);
        for (double t : roots) {
            if (t >= -1e-12 && t <= 1.0 + 1e-12) {
                double tx = evalQuadratic(x0, x1, x2, t);
                if (Double.isFinite(tx)) {
                    out.add(tx);
                }
            }
        }
    }

    private static void processCubicIntersections(double x0, double y0,
            double x1, double y1,
            double x2, double y2,
            double x3, double y3,
            double y, List<Double> out) {
        double a = -y0 + 3 * y1 - 3 * y2 + y3;
        double b = 3 * y0 - 6 * y1 + 3 * y2;
        double c = -3 * y0 + 3 * y1;
        double d = y0 - y;
        double[] roots = solveCubic(a, b, c, d);
        for (double t : roots) {
            if (t >= -1e-12 && t <= 1.0 + 1e-12) {
                double tx = evalCubic(x0, x1, x2, x3, t);
                if (Double.isFinite(tx)) {
                    out.add(tx);
                }
            }
        }
    }

    private static double evalQuadratic(double x0, double x1, double x2, double t) {
        double mt = 1.0 - t;
        return mt * mt * x0 + 2 * mt * t * x1 + t * t * x2;
    }

    private static double evalCubic(double x0, double x1, double x2, double x3, double t) {
        double mt = 1.0 - t;
        return mt * mt * mt * x0
                + 3 * mt * mt * t * x1
                + 3 * mt * t * t * x2
                + t * t * t * x3;
    }

    // -------------------- polynomial solvers --------------------
    private static double[] solveQuadratic(double a, double b, double c) {
        if (Math.abs(a) < 1e-14) {
            if (Math.abs(b) < 1e-14) {
                return new double[0];
            }
            return new double[]{-c / b};
        }
        double disc = b * b - 4 * a * c;
        if (disc < -1e-12) {
            return new double[0];
        }
        if (Math.abs(disc) < 1e-12) {
            return new double[]{-b / (2 * a)};
        }
        double sd = Math.sqrt(Math.max(0.0, disc));
        double t1 = (-b + sd) / (2 * a);
        double t2 = (-b - sd) / (2 * a);
        return new double[]{t1, t2};
    }

    private static double[] solveCubic(double a, double b, double c, double d) {
        if (Math.abs(a) < 1e-14) {
            return solveQuadratic(b, c, d);
        }
        double an = b / a;
        double bn = c / a;
        double cn = d / a;
        double Q = (3.0 * bn - an * an) / 9.0;
        double R = (9.0 * an * bn - 27.0 * cn - 2.0 * an * an * an) / 54.0;
        double D = Q * Q * Q + R * R;
        List<Double> roots = new ArrayList<>();
        if (D >= -1e-14) {
            double sqrtD = Math.sqrt(Math.max(0.0, D));
            double S = cbrt(R + sqrtD);
            double T = cbrt(R - sqrtD);
            double x = -an / 3.0 + (S + T);
            roots.add(x);
            double realPart = -an / 3.0 - (S + T) / 2.0;
            double imagPart = Math.abs(Math.sqrt(3.0) * (S - T) / 2.0);
            if (Math.abs(imagPart) < 1e-12) {
                roots.add(realPart);
                roots.add(realPart);
            }
        } else {
            double theta = Math.acos(Math.max(-1.0, Math.min(1.0, R / Math.sqrt(-Q * Q * Q))));
            double twoSqrtQ = 2.0 * Math.sqrt(-Q);
            double x1 = -an / 3.0 + twoSqrtQ * Math.cos(theta / 3.0);
            double x2 = -an / 3.0 + twoSqrtQ * Math.cos((theta + 2.0 * Math.PI) / 3.0);
            double x3 = -an / 3.0 + twoSqrtQ * Math.cos((theta + 4.0 * Math.PI) / 3.0);
            roots.add(x1);
            roots.add(x2);
            roots.add(x3);
        }
        return roots.stream().filter(r -> Double.isFinite(r)).mapToDouble(Double::doubleValue).toArray();
    }

    private static double cbrt(double v) {
        if (v >= 0.0) {
            return Math.pow(v, 1.0 / 3.0);
        } else {
            return -Math.pow(-v, 1.0 / 3.0);
        }
    }

    // -------------------- High-level intersection API --------------------
    /**
     * Intersect polyline/path with horizontal line y and return sorted X
     * intersections. Prefer analytic 'd' solver; fallback to SvgPathToJts and
     * then to sampled-points.
     */
    public static List<Double> intersectHorizontalXs(Curve2D curve, double y) {
        if (curve == null) {
            return List.of();
        }

        // 1) Analytic from normalized d if available
        String d = curve.getD();
        if (d != null && !d.trim().isEmpty()) {
            try {
                List<Double> res = intersectHorizontalXsFromPathDAnalytic(d, y);
                if (!res.isEmpty()) {
                    return res;
                }
                // if empty, continue to JTS fallback to catch overlapping segments etc.
            } catch (Throwable t) {
                // fall through to next method
            }
        }

        // 2) Try SvgPathToJts intersection with nearest fallback (if available)
        if (d != null && !d.trim().isEmpty()) {
            try {
                String norm = SvgPathEditor.normalizePath(d);
                double[] extent = computeCurveXExtent(curve);
                double[] results = SvgPathToJts.intersectHorizontalXsFromPathDWithNearestFallback(
                        norm, y, FLATNESS_MM, extent[0] - 100.0, extent[1] + 100.0, JTS_NEAREST_TOLERANCE_MM);
                if (results != null && results.length > 0) {
                    List<Double> xs = new ArrayList<>();
                    for (double v : results) {
                        xs.add(v);
                    }
                    Collections.sort(xs);
                    return xs;
                }
            } catch (Throwable t) {
                // ignore and fallback
            }
        }

        // 3) Fallback: sample polyline points stored in Curve2D
        if (curve.getPoints() == null || curve.getPoints().size() < 2) {
            return List.of();
        }
        List<Double> xs = new ArrayList<>();
        List<Pt> pts = curve.getPoints();
        for (int i = 0; i < pts.size() - 1; ++i) {
            Pt a = pts.get(i), b = pts.get(i + 1);
            if (a == null || b == null) {
                continue;
            }
            double y0 = a.getY(), y1 = b.getY();
            if (!Double.isFinite(y0) || !Double.isFinite(y1)) {
                continue;
            }
            if (Math.abs(y1 - y0) < EPS) {
                continue;
            }
            double minY = Math.min(y0, y1);
            double maxY = Math.max(y0, y1);
            if (y < minY - 1e-12 || y > maxY + 1e-12) {
                continue;
            }
            double t = (y - y0) / (y1 - y0);
            double x = a.getX() + t * (b.getX() - a.getX());
            if (Double.isFinite(x)) {
                xs.add(x);
            }
        }
        Collections.sort(xs);
        return xs;
    }

    private static OptionalDouble minXAtY(Curve2D curve, double y) {
        List<Double> xs = intersectHorizontalXs(curve, y);
        if (xs.isEmpty()) {
            return OptionalDouble.empty();
        }
        double m = xs.get(0);
        for (double v : xs) {
            m = Math.min(m, v);
        }
        return OptionalDouble.of(m);
    }

    private static OptionalDouble maxXAtY(Curve2D curve, double y) {
        List<Double> xs = intersectHorizontalXs(curve, y);
        if (xs.isEmpty()) {
            return OptionalDouble.empty();
        }
        double m = xs.get(0);
        for (double v : xs) {
            m = Math.max(m, v);
        }
        return OptionalDouble.of(m);
    }

    private static Curve2D preferNonEmpty(Curve2D primary, Curve2D fallback) {
        if (primary != null && primary.getPoints() != null && !primary.getPoints().isEmpty()) {
            return primary;
        }
        if (fallback != null && fallback.getPoints() != null && !fallback.getPoints().isEmpty()) {
            return fallback;
        }
        return primary != null ? primary : fallback;
    }

    /**
     * Try to obtain an X coordinate on the left side (wantMin=true) or right
     * side (wantMin=false) by trying preferred curve and then fallback curve.
     */
    private static OptionalDouble findXForSide(Curve2D preferred, Curve2D fallback, double y, boolean wantMin) {
        if (preferred != null) {
            OptionalDouble v = wantMin ? minXAtY(preferred, y) : maxXAtY(preferred, y);
            if (v.isPresent()) {
                return v;
            }
        }
        if (fallback != null) {
            OptionalDouble v2 = wantMin ? minXAtY(fallback, y) : maxXAtY(fallback, y);
            if (v2.isPresent()) {
                return v2;
            }
        }
        return OptionalDouble.empty();
    }

    /**
     * Width of one panel at dyMm (0 at waist, + up, - down). Width = xRight -
     * xLeft at y = waistY - dyMm.
     *
     * Left boundary = seamToPrev (prefer based on direction) Right boundary =
     * seamToNext (prefer based on direction) For positive dyMm (upwards):
     * prefer UP curves, fallback to DOWN (but only if preferred yields no
     * intersections) For negative dyMm (downwards): prefer DOWN curves,
     * fallback to UP
     */
    public static OptionalDouble computePanelWidthAtDy(PanelCurves panel, double dyMm) {
        if (panel == null) {
            return OptionalDouble.empty();
        }

        double waistY = computePanelWaistY0(panel.getWaist());
        double y = waistY - dyMm;

        // raw candidates (both up/down on both sides)
        Curve2D leftUp = panel.getSeamToPrevUp();
        Curve2D leftDown = panel.getSeamToPrevDown();
        Curve2D rightUp = panel.getSeamToNextUp();
        Curve2D rightDown = panel.getSeamToNextDown();

        // decide preference based on measurement direction
        boolean preferUp = dyMm >= 0;

        OptionalDouble xL;
        OptionalDouble xR;

        if (preferUp) {
            xL = findXForSide(leftUp, leftDown, y, true);   // left: want min X
            xR = findXForSide(rightUp, rightDown, y, false); // right: want max X
        } else {
            xL = findXForSide(leftDown, leftUp, y, true);
            xR = findXForSide(rightDown, rightUp, y, false);
        }

        if (xL.isEmpty() || xR.isEmpty()) {
            return OptionalDouble.empty();
        }

        double w = xR.getAsDouble() - xL.getAsDouble();
        return OptionalDouble.of(Math.abs(w));
    }

    // -------------------- Circumference & range helpers --------------------
    public static double computeHalfCircumference(List<PanelCurves> panels, double dyMm) {
        if (panels == null || panels.isEmpty()) {
            return 0.0;
        }
        double sum = 0.0;
        for (PanelCurves p : panels) {
            OptionalDouble w = computePanelWidthAtDy(p, dyMm);
            if (w.isPresent()) {
                sum += w.getAsDouble();
            }
        }
        return sum;
    }

    public static double computeHalfWaistCircumference(List<PanelCurves> panels) {
        if (panels == null || panels.isEmpty()) {
            return 0.0;
        }
        double sum = 0.0;
        for (PanelCurves p : panels) {
            if (p != null && p.getWaist() != null) {
                sum += computeCurveLength(p.getWaist());
            }
        }
        return sum;
    }

    public static double computeFullWaistCircumference(List<PanelCurves> panels) {
        return 2.0 * computeHalfWaistCircumference(panels);
    }

    public static double computeFullCircumference(List<PanelCurves> panels, double dyMm) {
        if (Math.abs(dyMm) < DEAD_ZONE_MM) {
            return computeFullWaistCircumference(panels);
        }
        return 2.0 * computeHalfCircumference(panels, dyMm);
    }

    public static final class DyRange {

        private final double maxUpDy;
        private final double maxDownDy;

        public DyRange(double maxUpDy, double maxDownDy) {
            this.maxUpDy = maxUpDy;
            this.maxDownDy = maxDownDy;
        }

        public double getMaxUpDy() {
            return maxUpDy;
        }

        public double getMaxDownDy() {
            return maxDownDy;
        }
    }

    public static DyRange computeValidDyRange(List<PanelCurves> panels, double stepMm) {
        if (panels == null || panels.isEmpty()) {
            return new DyRange(0.0, 0.0);
        }
        stepMm = Math.max(MIN_STEP_SIZE, Math.abs(stepMm));

        double startUp = Math.max(DEAD_ZONE_MM, stepMm);
        double maxUpDy = 0.0;
        boolean foundUp = false;
        for (double dy = startUp; dy <= MAX_DY_SEARCH_DISTANCE; dy += stepMm) {
            boolean ok = true;
            for (PanelCurves p : panels) {
                if (computePanelWidthAtDy(p, dy).isEmpty()) {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                foundUp = true;
                maxUpDy = dy;
                break;
            }
        }
        if (foundUp) {
            for (double dy = maxUpDy + stepMm; dy <= MAX_DY_SEARCH_DISTANCE; dy += stepMm) {
                boolean ok = true;
                for (PanelCurves p : panels) {
                    if (computePanelWidthAtDy(p, dy).isEmpty()) {
                        ok = false;
                        break;
                    }
                }
                if (ok) {
                    maxUpDy = dy;
                } else {
                    break;
                }
            }
        }

        double startDown = -Math.max(DEAD_ZONE_MM, stepMm);
        double maxDownDy = 0.0;
        boolean foundDown = false;
        for (double dy = startDown; dy >= -MAX_DY_SEARCH_DISTANCE; dy -= stepMm) {
            boolean ok = true;
            for (PanelCurves p : panels) {
                if (computePanelWidthAtDy(p, dy).isEmpty()) {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                foundDown = true;
                maxDownDy = Math.abs(dy);
                break;
            }
        }
        if (foundDown) {
            for (double dy = -maxDownDy - stepMm; dy >= -MAX_DY_SEARCH_DISTANCE; dy -= stepMm) {
                boolean ok = true;
                for (PanelCurves p : panels) {
                    if (computePanelWidthAtDy(p, dy).isEmpty()) {
                        ok = false;
                        break;
                    }
                }
                if (ok) {
                    maxDownDy = Math.abs(dy);
                } else {
                    break;
                }
            }
        }

        return new DyRange(maxUpDy, maxDownDy);
    }

    public static DyRange computeValidDyRange(List<PanelCurves> panels) {
        return computeValidDyRange(panels, 2.0);
    }
}
