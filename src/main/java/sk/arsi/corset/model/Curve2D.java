package sk.arsi.corset.model;

import java.util.Collections;
import java.util.List;

public final class Curve2D {

    private final String id;
    private final String d; // original SVG path data
    private final List<Pt> points;

    public Curve2D(String id, String d, List<Pt> points) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("Curve id is required.");
        }
        if (points == null || points.size() < 2) {
            throw new IllegalArgumentException("Curve must have at least 2 points: " + id);
        }
        this.id = id;
        this.d = d; // can be null for synthetic curves
        this.points = Collections.unmodifiableList(points);
    }

    // Backward-compatible constructor for synthetic curves without SVG path data
    public Curve2D(String id, List<Pt> points) {
        this(id, null, points);
    }

    public String getId() {
        return id;
    }

    public String getD() {
        return d;
    }

    public List<Pt> getPoints() {
        return points;
    }

    public Pt getFirst() {
        return points.get(0);
    }

    public Pt getLast() {
        return points.get(points.size() - 1);
    }

}
