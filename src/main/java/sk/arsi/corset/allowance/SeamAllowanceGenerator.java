package sk.arsi.corset.allowance;

import sk.arsi.corset.model.Curve2D;
import sk.arsi.corset.model.Pt;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates seam allowance offset curves from seam polylines.
 */
public final class SeamAllowanceGenerator {

    /**
     * Generate an offset curve for a seam.
     * 
     * @param seam The original seam curve
     * @param allowanceMm The offset distance in mm
     * @param offsetToLeft If true, offset to the left (in the direction of travel), otherwise to the right
     * @return A new curve with offset points
     */
    public Curve2D generateOffset(Curve2D seam, double allowanceMm, boolean offsetToLeft) {
        if (seam == null) {
            throw new IllegalArgumentException("Seam cannot be null");
        }
        if (allowanceMm < 0) {
            throw new IllegalArgumentException("Allowance must be non-negative");
        }

        List<Pt> originalPoints = seam.getPoints();
        if (originalPoints.size() < 2) {
            throw new IllegalArgumentException("Seam must have at least 2 points");
        }

        List<Pt> offsetPoints = new ArrayList<>(originalPoints.size());

        for (int i = 0; i < originalPoints.size(); i++) {
            Pt current = originalPoints.get(i);
            
            // Calculate normal direction at this point
            double nx, ny;
            
            if (i == 0) {
                // First point: use normal from first segment
                Pt next = originalPoints.get(i + 1);
                double dx = next.getX() - current.getX();
                double dy = next.getY() - current.getY();
                double len = Math.sqrt(dx * dx + dy * dy);
                if (len > 0) {
                    // Normal is perpendicular to segment
                    // Left normal: rotate 90 degrees counterclockwise
                    nx = -dy / len;
                    ny = dx / len;
                } else {
                    nx = 0;
                    ny = 0;
                }
            } else if (i == originalPoints.size() - 1) {
                // Last point: use normal from last segment
                Pt prev = originalPoints.get(i - 1);
                double dx = current.getX() - prev.getX();
                double dy = current.getY() - prev.getY();
                double len = Math.sqrt(dx * dx + dy * dy);
                if (len > 0) {
                    nx = -dy / len;
                    ny = dx / len;
                } else {
                    nx = 0;
                    ny = 0;
                }
            } else {
                // Middle point: average normals from both adjacent segments
                Pt prev = originalPoints.get(i - 1);
                Pt next = originalPoints.get(i + 1);
                
                // Normal from previous segment
                double dx1 = current.getX() - prev.getX();
                double dy1 = current.getY() - prev.getY();
                double len1 = Math.sqrt(dx1 * dx1 + dy1 * dy1);
                
                // Normal from next segment
                double dx2 = next.getX() - current.getX();
                double dy2 = next.getY() - current.getY();
                double len2 = Math.sqrt(dx2 * dx2 + dy2 * dy2);
                
                double nx1 = 0, ny1 = 0, nx2 = 0, ny2 = 0;
                if (len1 > 0) {
                    nx1 = -dy1 / len1;
                    ny1 = dx1 / len1;
                }
                if (len2 > 0) {
                    nx2 = -dy2 / len2;
                    ny2 = dx2 / len2;
                }
                
                // Average and normalize
                nx = nx1 + nx2;
                ny = ny1 + ny2;
                double len = Math.sqrt(nx * nx + ny * ny);
                if (len > 0) {
                    nx /= len;
                    ny /= len;
                } else {
                    nx = 0;
                    ny = 0;
                }
            }
            
            // Flip normal direction if offsetting to the right
            if (!offsetToLeft) {
                nx = -nx;
                ny = -ny;
            }
            
            // Apply offset
            double offsetX = current.getX() + nx * allowanceMm;
            double offsetY = current.getY() + ny * allowanceMm;
            
            offsetPoints.add(new Pt(offsetX, offsetY));
        }

        return new Curve2D(seam.getId() + "_ALLOW", offsetPoints);
    }
}
