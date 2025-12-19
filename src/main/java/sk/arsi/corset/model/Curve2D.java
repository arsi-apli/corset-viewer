package sk.arsi.corset.model;

import java.util.Collections;
import java.util.List;

public final class Curve2D {

    private final String id;
    private final List<Pt> points;

    public Curve2D(String id, List<Pt> points) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("Curve id is required.");
        }
        if (points == null || points.size() < 2) {
            throw new IllegalArgumentException("Curve must have at least 2 points: " + id);
        }
        this.id = id;
        this.points = Collections.unmodifiableList(points);
    }

    public String getId() {
        return id;
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
