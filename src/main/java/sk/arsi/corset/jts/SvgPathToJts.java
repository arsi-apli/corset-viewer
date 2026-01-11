package sk.arsi.corset.jts;

import org.apache.batik.parser.AWTPathProducer;
import org.apache.batik.parser.PathParser;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.operation.distance.DistanceOp;

import java.awt.Shape;
import java.awt.geom.FlatteningPathIterator;
import java.awt.geom.PathIterator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Convert an SVG path 'd' string into JTS Geometry (LineString /
 * MultiLineString) using Batik to parse the path and a FlatteningPathIterator
 * to sample it.
 *
 * Provides helpers for intersection with a horizontal line and measuring length
 * inside a half-plane (above/below a given Y).
 */
public final class SvgPathToJts {

    private static final GeometryFactory GF = new GeometryFactory();

    private SvgPathToJts() {
    }

    /**
     * Parse 'd' into an AWT Shape using Batik.
     */
    private static Shape parseShape(String d) {
        PathParser parser = new PathParser();
        AWTPathProducer producer = new AWTPathProducer();
        producer.setWindingRule(PathIterator.WIND_NON_ZERO);
        parser.setPathHandler(producer);
        parser.parse(d);
        return producer.getShape();
    }

    /**
     * Convert an SVG path 'd' into a JTS Geometry. Uses FlatteningPathIterator
     * with provided flatness (in same units as 'd', e.g. mm).
     *
     * Returns: - LineString if path has a single subpath, - MultiLineString if
     * multiple subpaths were present, - empty GeometryCollection if nothing
     * parsed.
     *
     * Note: this function preserves the sequence of sampled points and produces
     * one LineString per subpath (MOVETO starts a new subpath).
     */
    public static Geometry pathDToGeometry(String d, double flatnessMm) {
        if (d == null || d.trim().isEmpty()) {
            return GF.createGeometryCollection(new Geometry[0]);
        }
        if (flatnessMm <= 0.0) {
            throw new IllegalArgumentException("flatnessMm must be > 0");
        }

        Shape shape = parseShape(d);
        PathIterator rawIt = shape.getPathIterator(null);
        FlatteningPathIterator it = new FlatteningPathIterator(rawIt, flatnessMm);

        double[] seg = new double[6];
        List<LineString> subpaths = new ArrayList<>();
        List<Coordinate> current = null;

        double lastX = Double.NaN, lastY = Double.NaN;

        while (!it.isDone()) {
            int type = it.currentSegment(seg);

            if (type == PathIterator.SEG_MOVETO) {
                // start new subpath
                if (current != null && current.size() >= 2) {
                    subpaths.add(GF.createLineString(current.toArray(new Coordinate[0])));
                }
                current = new ArrayList<>();
                double x = seg[0];
                double y = seg[1];
                current.add(new Coordinate(x, y));
                lastX = x;
                lastY = y;
            } else if (type == PathIterator.SEG_LINETO) {
                if (current == null) {
                    // implicit moveto before lineto (rare), create new subpath
                    current = new ArrayList<>();
                    current.add(new Coordinate(seg[0], seg[1]));
                } else {
                    double x = seg[0];
                    double y = seg[1];
                    // avoid duplicate consecutive identical points
                    if (Double.isNaN(lastX) || Math.abs(x - lastX) + Math.abs(y - lastY) > 1e-12) {
                        current.add(new Coordinate(x, y));
                        lastX = x;
                        lastY = y;
                    }
                }
            } else if (type == PathIterator.SEG_CLOSE) {
                if (current != null && current.size() >= 2) {
                    subpaths.add(GF.createLineString(current.toArray(new Coordinate[0])));
                }
                current = null;
                lastX = lastY = Double.NaN;
            }

            it.next();
        }

        // finalize last subpath
        if (current != null && current.size() >= 2) {
            subpaths.add(GF.createLineString(current.toArray(new Coordinate[0])));
        }

        if (subpaths.isEmpty()) {
            return GF.createGeometryCollection(new Geometry[0]);
        } else if (subpaths.size() == 1) {
            return subpaths.get(0);
        } else {
            LineString[] arr = subpaths.toArray(new LineString[0]);
            return GF.createMultiLineString(arr);
        }
    }

    /**
     * Intersect an SVG path 'd' with a horizontal line at y. Returns sorted X
     * coordinates. Handles Point, MultiPoint and overlapping LineString cases.
     *
     * worldMinX/worldMaxX define a horizontal segment long enough to cross the
     * seam; if unsure, provide sufficiently large values (e.g. global bounds).
     */
    public static double[] intersectHorizontalXsFromPathD(String d, double y, double flatnessMm,
            double worldMinX, double worldMaxX) {
        Geometry geom = pathDToGeometry(d, flatnessMm);
        if (geom == null || geom.isEmpty()) {
            return new double[0];
        }

        LineString horiz = GF.createLineString(new Coordinate[]{new Coordinate(worldMinX, y), new Coordinate(worldMaxX, y)});
        Geometry inter = geom.intersection(horiz);

        if (inter == null || inter.isEmpty()) {
            return new double[0];
        }

        List<Double> xs = new ArrayList<>();
        for (int i = 0; i < inter.getNumGeometries(); i++) {
            Geometry g = inter.getGeometryN(i);
            if (g instanceof Point) {
                xs.add(((Point) g).getX());
            } else if (g instanceof MultiPoint) {
                for (int j = 0; j < g.getNumGeometries(); j++) {
                    xs.add(((Point) g.getGeometryN(j)).getX());
                }
            } else if (g instanceof LineString) {
                // overlapping segment - include interval endpoints
                Coordinate[] cs = ((LineString) g).getCoordinates();
                double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
                for (Coordinate c : cs) {
                    min = Math.min(min, c.x);
                    max = Math.max(max, c.x);
                }
                xs.add(min);
                xs.add(max);
            } else {
                // geometry collection: fall back to coordinates
                for (Coordinate c : g.getCoordinates()) {
                    xs.add(c.x);
                }
            }
        }

        Collections.sort(xs);
        return xs.stream().mapToDouble(Double::doubleValue).toArray();
    }

    /**
     * If exact intersection returns empty, find nearest point on path to the
     * horizontal and return its X if within toleranceMm.
     */
    public static double[] intersectHorizontalXsFromPathDWithNearestFallback(String d, double y, double flatnessMm,
            double worldMinX, double worldMaxX,
            double toleranceMm) {
        double[] xs = intersectHorizontalXsFromPathD(d, y, flatnessMm, worldMinX, worldMaxX);
        if (xs.length > 0) {
            return xs;
        }

        Geometry geom = pathDToGeometry(d, flatnessMm);
        if (geom == null || geom.isEmpty()) {
            return new double[0];
        }

        LineString horiz = GF.createLineString(new Coordinate[]{new Coordinate(worldMinX, y), new Coordinate(worldMaxX, y)});

        DistanceOp dOp = new DistanceOp(geom, horiz);
        Coordinate[] nearest = dOp.nearestPoints();
        if (nearest == null || nearest.length < 2) {
            return new double[0];
        }

        Coordinate ptOnGeom = nearest[0];
        Coordinate ptOnHoriz = nearest[1];
        double dist = ptOnGeom.distance(ptOnHoriz);
        if (dist <= toleranceMm) {
            return new double[]{ptOnGeom.x};
        }
        return new double[0];
    }

    /**
     * Compute length of curve portion inside half-plane relative to waistY. If
     * above==true, compute length where y < waistY (the "above" half-plane).
     * If above==false, compute length where y >= waistY (the "below"
     * half-plane).
     *
     * Uses pathDToGeometry(d, flatnessMm) to obtain geometry and intersects
     * with a large polygon representing the half-plane bounded by minX..maxX
     * and an extreme Y guard.
     */
    public static double lengthOfCurveInHalfPlane(String d, double waistY, boolean above,
            double minX, double maxX, double flatnessMm) {
        if (d == null || d.trim().isEmpty()) {
            return 0.0;
        }
        try {
            Geometry g = pathDToGeometry(d, flatnessMm);
            if (g == null || g.isEmpty()) {
                return 0.0;
            }

            // Make a large polygon representing the half-plane constrained in X by minX..maxX.
            // Use very large Y extent to approximate infinite half-plane.
            double EXT = 1e6;
            Coordinate[] polyCoords;
            if (above) {
                // y < waistY
                polyCoords = new Coordinate[]{
                    new Coordinate(minX, -EXT),
                    new Coordinate(maxX, -EXT),
                    new Coordinate(maxX, waistY),
                    new Coordinate(minX, waistY),
                    new Coordinate(minX, -EXT)
                };
            } else {
                // y >= waistY
                polyCoords = new Coordinate[]{
                    new Coordinate(minX, waistY),
                    new Coordinate(maxX, waistY),
                    new Coordinate(maxX, EXT),
                    new Coordinate(minX, EXT),
                    new Coordinate(minX, waistY)
                };
            }
            Geometry halfPlane = GF.createPolygon(polyCoords);
            Geometry inter = g.intersection(halfPlane);
            if (inter == null || inter.isEmpty()) {
                return 0.0;
            }
            return inter.getLength();
        } catch (Exception ex) {
            return 0.0;
        }
    }
}
