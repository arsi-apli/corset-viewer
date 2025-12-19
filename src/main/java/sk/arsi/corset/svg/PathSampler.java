package sk.arsi.corset.svg;

import org.apache.batik.parser.AWTPathProducer;
import org.apache.batik.parser.PathParser;
import sk.arsi.corset.model.Curve2D;
import sk.arsi.corset.model.Pt;

import java.awt.Shape;
import java.awt.geom.FlatteningPathIterator;
import java.awt.geom.PathIterator;
import java.util.ArrayList;
import java.util.List;

public final class PathSampler {

    public Curve2D samplePath(String id, String d, double flatnessMm, double resampleStepMm) {
        if (d == null || d.trim().isEmpty()) {
            throw new IllegalArgumentException("Empty path 'd' for id=" + id);
        }
        if (flatnessMm <= 0.0) {
            throw new IllegalArgumentException("flatnessMm must be > 0");
        }
        if (resampleStepMm < 0.0) {
            throw new IllegalArgumentException("resampleStepMm must be >= 0");
        }

        Shape shape = parseShape(d);

        PathIterator rawIterator = shape.getPathIterator(null);
        FlatteningPathIterator it = new FlatteningPathIterator(rawIterator, flatnessMm);

        List<Pt> pts = new ArrayList<Pt>();
        double[] seg = new double[6];

        double lastX = Double.NaN;
        double lastY = Double.NaN;

        while (!it.isDone()) {
            int type = it.currentSegment(seg);

            if (type == PathIterator.SEG_MOVETO || type == PathIterator.SEG_LINETO) {
                double x = seg[0];
                double y = seg[1];

                if (Double.isNaN(lastX) || (Math.abs(x - lastX) + Math.abs(y - lastY)) > 1e-9) {
                    pts.add(new Pt(x, y));
                    lastX = x;
                    lastY = y;
                }
            }

            it.next();
        }

        if (resampleStepMm > 0.0) {
            List<Pt> resampled = resampleByStep(pts, resampleStepMm);
            return new Curve2D(id, resampled);
        }

        return new Curve2D(id, pts);
    }

    // Backward-compatible overload
    public Curve2D samplePath(String id, String d, double flatnessMm) {
        return samplePath(id, d, flatnessMm, 0.0);
    }

    private Shape parseShape(String d) {
        PathParser parser = new PathParser();
        AWTPathProducer producer = new AWTPathProducer();
        producer.setWindingRule(PathIterator.WIND_NON_ZERO);
        parser.setPathHandler(producer);
        parser.parse(d);
        return producer.getShape();
    }

    private List<Pt> resampleByStep(List<Pt> polyline, double stepMm) {
        if (polyline == null || polyline.size() < 2) {
            return polyline;
        }

        List<Pt> out = new ArrayList<Pt>();
        Pt first = polyline.get(0);
        out.add(first);

        double distanceFromLastSample = 0.0;
        Pt lastSample = first;

        for (int i = 1; i < polyline.size(); i++) {
            Pt a = polyline.get(i - 1);
            Pt b = polyline.get(i);

            double segLen = dist(a, b);
            if (segLen <= 1e-12) {
                continue;
            }

            double remaining = segLen;
            Pt segStart = a;

            while (distanceFromLastSample + remaining >= stepMm) {
                double need = stepMm - distanceFromLastSample;
                double t = need / remaining; // t along current (segStart -> b) portion

                Pt nextSample = lerp(segStart, b, t);
                out.add(nextSample);

                // advance along segment
                remaining = dist(nextSample, b);
                segStart = nextSample;

                lastSample = nextSample;
                distanceFromLastSample = 0.0;
            }

            distanceFromLastSample = distanceFromLastSample + remaining;
            lastSample = b;
        }

        // Ensure last point is present (helps with endpoints / intersections)
        Pt last = polyline.get(polyline.size() - 1);
        Pt lastOut = out.get(out.size() - 1);
        if (dist(last, lastOut) > 1e-6) {
            out.add(last);
        }

        return out;
    }

    private double dist(Pt a, Pt b) {
        double dx = b.getX() - a.getX();
        double dy = b.getY() - a.getY();
        return Math.hypot(dx, dy);
    }

    private Pt lerp(Pt a, Pt b, double t) {
        double x = a.getX() + (b.getX() - a.getX()) * t;
        double y = a.getY() + (b.getY() - a.getY()) * t;
        return new Pt(x, y);
    }
}
