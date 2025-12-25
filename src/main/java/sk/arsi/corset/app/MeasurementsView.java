package sk.arsi.corset.app;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Node;
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

/**
 * Measurements tab showing seam comparison tables.
 */
public final class MeasurementsView {

    private static final int FONT_TITLE = 18;
    private static final int FONT_LABEL = 15;

    private final VBox root;
    
    // Tolerance control
    private final Spinner<Double> toleranceSpinner;
    private final DoubleProperty toleranceProperty;
    
    // TOP table (from waist upwards)
    private final TableView<SeamMeasurementData> topTable;
    private final ObservableList<SeamMeasurementData> topData;
    
    // BOTTOM table (from waist downwards)
    private final TableView<SeamMeasurementData> bottomTable;
    private final ObservableList<SeamMeasurementData> bottomData;
    
    private List<PanelCurves> panels;
    private Consumer<Double> onToleranceChanged;

    public MeasurementsView() {
        this.root = new VBox(15.0);
        this.toleranceProperty = new SimpleDoubleProperty(0.5);
        this.toleranceSpinner = createToleranceSpinner();
        this.topData = FXCollections.observableArrayList();
        this.bottomData = FXCollections.observableArrayList();
        this.topTable = createSeamTable("TOP (from waist up)", topData);
        this.bottomTable = createSeamTable("BOTTOM (from waist down)", bottomData);
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

    public double getTolerance() {
        return toleranceProperty.get();
    }

    public DoubleProperty toleranceProperty() {
        return toleranceProperty;
    }

    private void initUi() {
        Label title = new Label("Seam Measurements");
        title.setStyle("-fx-font-weight: bold; -fx-font-size: " + FONT_TITLE + "px;");

        // Tolerance control
        Label toleranceLabel = new Label("Max Allowed Seam Mismatch (mm):");
        toleranceLabel.setStyle("-fx-font-size: " + FONT_LABEL + "px;");
        
        HBox toleranceBox = new HBox(10.0, toleranceLabel, toleranceSpinner);
        toleranceBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        root.getChildren().addAll(
                title,
                toleranceBox,
                topTable,
                bottomTable
        );
        
        root.setPadding(new Insets(10.0));
        VBox.setVgrow(topTable, Priority.ALWAYS);
        VBox.setVgrow(bottomTable, Priority.ALWAYS);
    }

    private Spinner<Double> createToleranceSpinner() {
        SpinnerValueFactory<Double> valueFactory = 
                new SpinnerValueFactory.DoubleSpinnerValueFactory(0.0, 10.0, 0.5, 0.1);
        
        Spinner<Double> spinner = new Spinner<>(valueFactory);
        spinner.setEditable(true);
        spinner.setPrefWidth(100.0);
        
        spinner.valueProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                toleranceProperty.set(newV);
                // Refresh both tables when tolerance changes
                topTable.refresh();
                bottomTable.refresh();
                if (onToleranceChanged != null) {
                    onToleranceChanged.accept(newV);
                }
            }
        });
        
        return spinner;
    }

    private TableView<SeamMeasurementData> createSeamTable(String title, ObservableList<SeamMeasurementData> data) {
        TableView<SeamMeasurementData> table = new TableView<>(data);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        
        // Seam column
        TableColumn<SeamMeasurementData, String> seamCol = new TableColumn<>("Seam");
        seamCol.setCellValueFactory(new PropertyValueFactory<>("seamName"));
        seamCol.setPrefWidth(60);
        
        // Left_UP column
        TableColumn<SeamMeasurementData, Double> leftUpCol = new TableColumn<>("Left_UP");
        leftUpCol.setCellValueFactory(data2 -> {
            double val = title.contains("TOP") 
                    ? data2.getValue().getLeftUpTop() 
                    : data2.getValue().getLeftUpBottom();
            return new javafx.beans.property.SimpleDoubleProperty(val).asObject();
        });
        leftUpCol.setCellFactory(col -> new javafx.scene.control.TableCell<SeamMeasurementData, Double>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(String.format("%.1f", item));
                }
            }
        });
        
        // Right_UP column
        TableColumn<SeamMeasurementData, Double> rightUpCol = new TableColumn<>("Right_UP");
        rightUpCol.setCellValueFactory(data2 -> {
            double val = title.contains("TOP") 
                    ? data2.getValue().getRightUpTop() 
                    : data2.getValue().getRightUpBottom();
            return new javafx.beans.property.SimpleDoubleProperty(val).asObject();
        });
        rightUpCol.setCellFactory(col -> new javafx.scene.control.TableCell<SeamMeasurementData, Double>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(String.format("%.1f", item));
                }
            }
        });
        
        // Diff_UP column
        TableColumn<SeamMeasurementData, Double> diffUpCol = new TableColumn<>("Diff_UP");
        diffUpCol.setCellValueFactory(data2 -> {
            double val = title.contains("TOP") 
                    ? data2.getValue().getDiffUpTop() 
                    : data2.getValue().getDiffUpBottom();
            return new javafx.beans.property.SimpleDoubleProperty(val).asObject();
        });
        diffUpCol.setCellFactory(col -> new javafx.scene.control.TableCell<SeamMeasurementData, Double>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(String.format("%.1f", item));
                    if (Math.abs(item) > toleranceProperty.get()) {
                        setStyle("-fx-background-color: #ffcccc;");
                    } else {
                        setStyle("");
                    }
                }
            }
        });
        
        // Left_DOWN column
        TableColumn<SeamMeasurementData, Double> leftDownCol = new TableColumn<>("Left_DN");
        leftDownCol.setCellValueFactory(data2 -> {
            double val = title.contains("TOP") 
                    ? data2.getValue().getLeftDownTop() 
                    : data2.getValue().getLeftDownBottom();
            return new javafx.beans.property.SimpleDoubleProperty(val).asObject();
        });
        leftDownCol.setCellFactory(col -> new javafx.scene.control.TableCell<SeamMeasurementData, Double>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(String.format("%.1f", item));
                }
            }
        });
        
        // Right_DOWN column
        TableColumn<SeamMeasurementData, Double> rightDownCol = new TableColumn<>("Right_DN");
        rightDownCol.setCellValueFactory(data2 -> {
            double val = title.contains("TOP") 
                    ? data2.getValue().getRightDownTop() 
                    : data2.getValue().getRightDownBottom();
            return new javafx.beans.property.SimpleDoubleProperty(val).asObject();
        });
        rightDownCol.setCellFactory(col -> new javafx.scene.control.TableCell<SeamMeasurementData, Double>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(String.format("%.1f", item));
                }
            }
        });
        
        // Diff_DOWN column
        TableColumn<SeamMeasurementData, Double> diffDownCol = new TableColumn<>("Diff_DN");
        diffDownCol.setCellValueFactory(data2 -> {
            double val = title.contains("TOP") 
                    ? data2.getValue().getDiffDownTop() 
                    : data2.getValue().getDiffDownBottom();
            return new javafx.beans.property.SimpleDoubleProperty(val).asObject();
        });
        diffDownCol.setCellFactory(col -> new javafx.scene.control.TableCell<SeamMeasurementData, Double>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(String.format("%.1f", item));
                    if (Math.abs(item) > toleranceProperty.get()) {
                        setStyle("-fx-background-color: #ffcccc;");
                    } else {
                        setStyle("");
                    }
                }
            }
        });
        
        table.getColumns().addAll(seamCol, leftUpCol, rightUpCol, diffUpCol, leftDownCol, rightDownCol, diffDownCol);
        table.setPlaceholder(new Label("No seam measurements available"));
        
        return table;
    }

    private void updateMeasurements() {
        List<SeamMeasurementData> measurements = SeamMeasurementService.computeAllSeamMeasurements(panels);
        
        topData.clear();
        bottomData.clear();
        
        topData.addAll(measurements);
        bottomData.addAll(measurements);
        
        // Refresh tables to update highlighting
        topTable.refresh();
        bottomTable.refresh();
    }
}
