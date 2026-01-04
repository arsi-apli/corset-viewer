package sk.arsi.corset.app;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import sk.arsi.corset.measure.SeamMeasurementData;
import sk.arsi.corset.measure.SeamMeasurementService;
import sk.arsi.corset.model.PanelCurves;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.ToDoubleFunction;

/**
 * Measurements tab showing seam comparison tables.
 *
 * Intended semantics: - Upper table: Top seams (UP curve), show only TOP
 * portion (above waist): leftUpTop/rightUpTop/diffUpTop - Lower table: Bottom
 * seams (DN curve), show only BOTTOM portion (below waist):
 * leftDownBottom/rightDownBottom/diffDownBottom
 *
 * Each table has 4 columns: Seam | Left | Right | Diff
 */
public final class MeasurementsView {

    private static final int FONT_TITLE = 18;
    private static final int FONT_LABEL = 15;

    private final VBox root;

    // Tolerance control
    private final Spinner<Double> toleranceSpinner;
    private final DoubleProperty toleranceProperty;

    // Allowance control
    private final Spinner<Double> allowanceSpinner;
    private final DoubleProperty allowanceProperty;
    private final Button exportButton;

    // Top seams (UP, above waist)
    private final TableView<SeamMeasurementData> topTable;
    private final ObservableList<SeamMeasurementData> topData;

    // Bottom seams (DN, below waist)
    private final TableView<SeamMeasurementData> bottomTable;
    private final ObservableList<SeamMeasurementData> bottomData;

    private List<PanelCurves> panels;
    private Consumer<Double> onToleranceChanged;
    private Runnable onExportRequested;

    public MeasurementsView() {
        this.root = new VBox(15.0);
        this.toleranceProperty = new SimpleDoubleProperty(0.5);
        this.toleranceSpinner = createToleranceSpinner();

        this.allowanceProperty = new SimpleDoubleProperty(10.0);
        this.allowanceSpinner = createAllowanceSpinner();
        this.exportButton = createExportButton();

        this.topData = FXCollections.observableArrayList();
        this.bottomData = FXCollections.observableArrayList();

        this.topTable = createTopSeamsTable(topData);
        this.bottomTable = createBottomSeamsTable(bottomData);

        this.panels = new ArrayList<>();

        initUi();
    }

    public Node getNode() {
        return root;
    }

    public void setPanels(List<PanelCurves> panels) {
        this.panels = panels != null ? panels : new ArrayList<>();
        updateMeasurements();
    }

    public void setOnToleranceChanged(Consumer<Double> callback) {
        this.onToleranceChanged = callback;
    }

    public void setOnExportRequested(Runnable callback) {
        this.onExportRequested = callback;
    }

    public double getTolerance() {
        return toleranceProperty.get();
    }

    public DoubleProperty toleranceProperty() {
        return toleranceProperty;
    }

    public double getAllowance() {
        return allowanceProperty.get();
    }

    public DoubleProperty allowanceProperty() {
        return allowanceProperty;
    }

    private void initUi() {
        Label title = new Label("Seam Measurements");
        title.setStyle("-fx-font-weight: bold; -fx-font-size: " + FONT_TITLE + "px;");

        // Tolerance control
        Label toleranceLabel = new Label("Max Allowed Seam Mismatch (mm):");
        toleranceLabel.setStyle("-fx-font-size: " + FONT_LABEL + "px;");

        HBox toleranceBox = new HBox(10.0, toleranceLabel, toleranceSpinner);
        toleranceBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        // Allowance control and export button
        Label allowanceLabel = new Label("Seam Allowance (mm):");
        allowanceLabel.setStyle("-fx-font-size: " + FONT_LABEL + "px;");

        HBox allowanceBox = new HBox(10.0, allowanceLabel, allowanceSpinner, exportButton);
        allowanceBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        Label topLbl = new Label("Top seams (UP)");
        topLbl.setStyle("-fx-font-weight: bold; -fx-font-size: " + FONT_LABEL + "px;");

        Label bottomLbl = new Label("Bottom seams (DN)");
        bottomLbl.setStyle("-fx-font-weight: bold; -fx-font-size: " + FONT_LABEL + "px;");

        root.getChildren().addAll(
                title,
                toleranceBox,
                allowanceBox,
                topLbl,
                topTable,
                bottomLbl,
                bottomTable
        );

        root.setPadding(new Insets(10.0));
        VBox.setVgrow(topTable, Priority.ALWAYS);
        VBox.setVgrow(bottomTable, Priority.ALWAYS);
    }

    private Spinner<Double> createToleranceSpinner() {
        SpinnerValueFactory<Double> valueFactory
                = new SpinnerValueFactory.DoubleSpinnerValueFactory(0.0, 10.0, 0.5, 0.1);

        Spinner<Double> spinner = new Spinner<>(valueFactory);
        spinner.setEditable(true);
        spinner.setPrefWidth(100.0);

        spinner.valueProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                toleranceProperty.set(newV);

                // Refresh both tables when tolerance changes (diff highlighting depends on tolerance)
                topTable.refresh();
                bottomTable.refresh();

                if (onToleranceChanged != null) {
                    onToleranceChanged.accept(newV);
                }
            }
        });

        return spinner;
    }

    private Spinner<Double> createAllowanceSpinner() {
        SpinnerValueFactory<Double> valueFactory
                = new SpinnerValueFactory.DoubleSpinnerValueFactory(0.0, 100.0, 10.0, 0.5);

        Spinner<Double> spinner = new Spinner<>(valueFactory);
        spinner.setEditable(true);
        spinner.setPrefWidth(100.0);

        spinner.valueProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                allowanceProperty.set(newV);
            }
        });

        return spinner;
    }

    private Button createExportButton() {
        Button button = new Button("Export SVG (with allowances)");
        button.setOnAction(e -> {
            if (onExportRequested != null) {
                onExportRequested.run();
            }
        });
        return button;
    }

    private TableView<SeamMeasurementData> createTopSeamsTable(ObservableList<SeamMeasurementData> data) {
        TableView<SeamMeasurementData> table = new TableView<>(data);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<SeamMeasurementData, String> seamCol = new TableColumn<>("Seam");
        seamCol.setCellValueFactory(new PropertyValueFactory<>("seamName"));
        seamCol.setPrefWidth(60);

        TableColumn<SeamMeasurementData, Double> leftCol
                = createNumberColumn("Left", SeamMeasurementData::getLeftUpTop, false);

        TableColumn<SeamMeasurementData, Double> rightCol
                = createNumberColumn("Right", SeamMeasurementData::getRightUpTop, false);

        TableColumn<SeamMeasurementData, Double> diffCol
                = createNumberColumn("Diff", SeamMeasurementData::getDiffUpTop, true);

        table.getColumns().addAll(seamCol, leftCol, rightCol, diffCol);
        table.setPlaceholder(new Label("No seam measurements available"));
        return table;
    }

    private TableView<SeamMeasurementData> createBottomSeamsTable(ObservableList<SeamMeasurementData> data) {
        TableView<SeamMeasurementData> table = new TableView<>(data);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<SeamMeasurementData, String> seamCol = new TableColumn<>("Seam");
        seamCol.setCellValueFactory(new PropertyValueFactory<>("seamName"));
        seamCol.setPrefWidth(60);

        TableColumn<SeamMeasurementData, Double> leftCol
                = createNumberColumn("Left", SeamMeasurementData::getLeftDownBottom, false);

        TableColumn<SeamMeasurementData, Double> rightCol
                = createNumberColumn("Right", SeamMeasurementData::getRightDownBottom, false);

        TableColumn<SeamMeasurementData, Double> diffCol
                = createNumberColumn("Diff", SeamMeasurementData::getDiffDownBottom, true);

        table.getColumns().addAll(seamCol, leftCol, rightCol, diffCol);
        table.setPlaceholder(new Label("No seam measurements available"));
        return table;
    }

    private TableColumn<SeamMeasurementData, Double> createNumberColumn(
            String header,
            ToDoubleFunction<SeamMeasurementData> getter,
            boolean highlightDiff
    ) {
        TableColumn<SeamMeasurementData, Double> col = new TableColumn<>(header);

        col.setCellValueFactory(data2
                -> new javafx.beans.property.SimpleDoubleProperty(getter.applyAsDouble(data2.getValue())).asObject());

        col.setCellFactory(c -> new javafx.scene.control.TableCell<SeamMeasurementData, Double>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);

                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                    return;
                }

                setText(String.format("%.1f", item));

                if (highlightDiff) {
                    if (Math.abs(item) > toleranceProperty.get()) {
                        setStyle("-fx-background-color: #ffcccc;");
                    } else {
                        setStyle("");
                    }
                } else {
                    setStyle("");
                }
            }
        });

        return col;
    }

    private void updateMeasurements() {
        List<SeamMeasurementData> measurements = SeamMeasurementService.computeAllSeamMeasurements(panels);

        topData.clear();
        bottomData.clear();

        // Both tables show the same seam rows (AB..EF),
        // but each table binds to different fields (UP/TOP vs DN/BOTTOM).
        topData.addAll(measurements);
        bottomData.addAll(measurements);

        topTable.refresh();
        bottomTable.refresh();
    }
}
