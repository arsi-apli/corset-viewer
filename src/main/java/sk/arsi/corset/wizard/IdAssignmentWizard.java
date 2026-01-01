package sk.arsi.corset.wizard;

import javafx.geometry.Insets;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import sk.arsi.corset.model.Pt;

import java.util.List;

/**
 * Interactive dialog for assigning required IDs to SVG path elements.
 */
public final class IdAssignmentWizard extends Dialog<Boolean> {

    private final IdWizardSession session;
    private final Canvas canvas;
    private final Label stepLabel;
    private final Button nextButton;

    private SvgPathCandidate hoveredCandidate;
    private SvgPathCandidate selectedCandidate;

    private double viewScaleX;
    private double viewScaleY;
    private double viewOffsetX;
    private double viewOffsetY;

    public IdAssignmentWizard(IdWizardSession session) {
        this.session = session;

        setTitle("Assigning IDs for SVG curves");
        setHeaderText("Click on the curve to assign the desired ID.");

        // Canvas for rendering
        canvas = new Canvas(800, 600);
        canvas.setOnMouseMoved(this::handleMouseMoved);
        canvas.setOnMouseClicked(this::handleMouseClicked);

        // Step label
        stepLabel = new Label();
        updateStepLabel();

        // Buttons
        nextButton = new Button("Next");
        nextButton.setDisable(true);
        nextButton.setOnAction(e -> handleNext());

        Button exitButton = new Button("Exit");
        exitButton.setOnAction(e -> handleExit());

        HBox buttons = new HBox(10, nextButton, exitButton);

        // Layout
        VBox top = new VBox(10, stepLabel);
        top.setPadding(new Insets(10));

        VBox bottom = new VBox(10, buttons);
        bottom.setPadding(new Insets(10));

        BorderPane root = new BorderPane();
        root.setTop(top);
        root.setCenter(canvas);
        root.setBottom(bottom);

        getDialogPane().setContent(root);
        getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        getDialogPane().lookupButton(ButtonType.CLOSE).setVisible(false);

        // Compute view transform
        computeViewTransform();

        // Initial render
        render();

        setResizable(true);
    }

    private void updateStepLabel() {
        RequiredPath current = session.currentStep();
        if (current != null) {
            stepLabel.setText(String.format("Assign: %s (%d/%d)",
                    current.svgId(),
                    session.currentStepNumber(),
                    session.totalMissing()));
        } else {
            stepLabel.setText("Finished");
        }
    }

    private void handleMouseMoved(MouseEvent e) {
        double mouseX = e.getX();
        double mouseY = e.getY();

        SvgPathCandidate nearest = findNearestCandidate(mouseX, mouseY, 10.0);

        if (nearest != hoveredCandidate) {
            hoveredCandidate = nearest;
            render();
        }
    }

    private void handleMouseClicked(MouseEvent e) {
        double mouseX = e.getX();
        double mouseY = e.getY();

        SvgPathCandidate nearest = findNearestCandidate(mouseX, mouseY, 10.0);

        if (nearest != null && !nearest.isGreen()) {
            selectedCandidate = nearest;
            nextButton.setDisable(false);
            render();
        }
    }

    private void handleNext() {
        if (selectedCandidate != null) {
            session.assignCurrent(selectedCandidate);
            selectedCandidate = null;
            hoveredCandidate = null;
            nextButton.setDisable(true);

            if (session.isComplete()) {
                // Wizard complete
                setResult(true);
                close();
            } else {
                updateStepLabel();
                render();
            }
        }
    }

    private void handleExit() {
        setResult(false);
        close();
    }

    /**
     * Find the nearest candidate to the mouse position within the given
     * threshold.
     */
    private SvgPathCandidate findNearestCandidate(double mouseX, double mouseY, double threshold) {
        SvgPathCandidate nearest = null;
        double minDistance = threshold;

        for (SvgPathCandidate candidate : session.getCandidates()) {
            List<Pt> polyline = candidate.getPolyline();

            for (int i = 0; i < polyline.size() - 1; i++) {
                Pt p1 = polyline.get(i);
                Pt p2 = polyline.get(i + 1);

                double x1 = transformX(p1.getX());
                double y1 = transformY(p1.getY());
                double x2 = transformX(p2.getX());
                double y2 = transformY(p2.getY());

                double dist = distanceToSegment(mouseX, mouseY, x1, y1, x2, y2);

                if (dist < minDistance) {
                    minDistance = dist;
                    nearest = candidate;
                }
            }
        }

        return nearest;
    }

    /**
     * Compute distance from point (px, py) to line segment (x1, y1)-(x2, y2).
     */
    private double distanceToSegment(double px, double py, double x1, double y1, double x2, double y2) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double lengthSquared = dx * dx + dy * dy;

        if (lengthSquared < 1e-10) {
            // Degenerate segment
            return Math.hypot(px - x1, py - y1);
        }

        double t = Math.max(0, Math.min(1, ((px - x1) * dx + (py - y1) * dy) / lengthSquared));
        double nearestX = x1 + t * dx;
        double nearestY = y1 + t * dy;

        return Math.hypot(px - nearestX, py - nearestY);
    }

    /**
     * Compute view transform to fit all paths in the canvas.
     */
    private void computeViewTransform() {
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;

        for (SvgPathCandidate candidate : session.getCandidates()) {
            for (Pt pt : candidate.getPolyline()) {
                minX = Math.min(minX, pt.getX());
                minY = Math.min(minY, pt.getY());
                maxX = Math.max(maxX, pt.getX());
                maxY = Math.max(maxY, pt.getY());
            }
        }

        double svgWidth = maxX - minX;
        double svgHeight = maxY - minY;

        if (svgWidth <= 0 || svgHeight <= 0) {
            viewScaleX = 1.0;
            viewScaleY = 1.0;
            viewOffsetX = 0.0;
            viewOffsetY = 0.0;
            return;
        }

        double canvasWidth = canvas.getWidth();
        double canvasHeight = canvas.getHeight();

        double padding = 20.0;
        double scale = Math.min((canvasWidth - 2 * padding) / svgWidth,
                (canvasHeight - 2 * padding) / svgHeight);

        viewScaleX = scale;
        viewScaleY = scale;
        viewOffsetX = padding + (canvasWidth - 2 * padding - svgWidth * scale) / 2 - minX * scale;
        viewOffsetY = padding + (canvasHeight - 2 * padding - svgHeight * scale) / 2 - minY * scale;
    }

    private double transformX(double x) {
        return x * viewScaleX + viewOffsetX;
    }

    private double transformY(double y) {
        return y * viewScaleY + viewOffsetY;
    }

    /**
     * Render all paths on the canvas.
     */
    private void render() {
        GraphicsContext gc = canvas.getGraphicsContext2D();

        // Clear
        gc.setFill(Color.WHITE);
        gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());

        // Draw all paths
        for (SvgPathCandidate candidate : session.getCandidates()) {
            Color color;
            double lineWidth;

            if (candidate == selectedCandidate) {
                color = Color.RED;
                lineWidth = 2.5;
            } else if (candidate == hoveredCandidate && !candidate.isGreen()) {
                color = Color.BLUE;
                lineWidth = 2.0;
            } else if (candidate.isGreen()) {
                color = Color.GREEN;
                lineWidth = 1.5;
            } else {
                color = Color.BLACK;
                lineWidth = 1.0;
            }

            gc.setStroke(color);
            gc.setLineWidth(lineWidth);

            List<Pt> polyline = candidate.getPolyline();
            for (int i = 0; i < polyline.size() - 1; i++) {
                Pt p1 = polyline.get(i);
                Pt p2 = polyline.get(i + 1);

                double x1 = transformX(p1.getX());
                double y1 = transformY(p1.getY());
                double x2 = transformX(p2.getX());
                double y2 = transformY(p2.getY());

                gc.strokeLine(x1, y1, x2, y2);
            }
        }
    }
}
