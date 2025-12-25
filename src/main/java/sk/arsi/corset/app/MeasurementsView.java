package sk.arsi.corset.app;

import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.data.provider.ListDataProvider;

import sk.arsi.corset.app.common.AppliedTolerance;
import sk.arsi.corset.app.common.AppTheme;
import sk.arsi.corset.app.common.ModelWrapper;
import sk.arsi.corset.app.data.SeamMeasurementData;

import java.util.ArrayList;
import java.util.List;

/**
 * Displays measurements in the application.
 */
public class MeasurementsView extends VerticalLayout {

    private final List<SeamMeasurementData> seamMeasurementData = new ArrayList<>();

    private final ListDataProvider<SeamMeasurementData> topData = new ListDataProvider<>(seamMeasurementData);
    private final ListDataProvider<SeamMeasurementData> bottomData = new ListDataProvider<>(seamMeasurementData);

    private final Grid<SeamMeasurementData> topGrid = new Grid<>(SeamMeasurementData.class, false);
    private final Grid<SeamMeasurementData> bottomGrid = new Grid<>(SeamMeasurementData.class, false);

    public MeasurementsView() {
        setSizeFull();
        setPadding(true);
        setSpacing(true);
        addClassName(AppTheme.MEASUREMENTS_VIEW);

        add(new H3("Seam measurements"));

        configureTopGrid();
        configureBottomGrid();

        HorizontalLayout grids = new HorizontalLayout(topGrid, bottomGrid);
        grids.setWidthFull();
        grids.setSpacing(true);
        grids.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.STRETCH);

        // each grid takes half of the available width
        topGrid.setWidth("50%");
        bottomGrid.setWidth("50%");

        add(grids);
    }

    private void configureTopGrid() {
        topGrid.setDataProvider(topData);
        topGrid.setSizeFull();

        topGrid.addColumn(SeamMeasurementData::getSeam)
                .setHeader("Seam")
                .setAutoWidth(true)
                .setFlexGrow(0);

        topGrid.addColumn(SeamMeasurementData::getLeftUpTop)
                .setHeader("Left")
                .setAutoWidth(true);

        topGrid.addColumn(SeamMeasurementData::getRightUpTop)
                .setHeader("Right")
                .setAutoWidth(true);

        topGrid.addColumn(SeamMeasurementData::getDiffUpTop)
                .setHeader("Diff")
                .setAutoWidth(true)
                .setClassNameGenerator(this::diffClassForUpTop);

        topGrid.getColumns().forEach(c -> c.setResizable(true));
        topGrid.setAllRowsVisible(true);
    }

    private void configureBottomGrid() {
        bottomGrid.setDataProvider(bottomData);
        bottomGrid.setSizeFull();

        bottomGrid.addColumn(SeamMeasurementData::getSeam)
                .setHeader("Seam")
                .setAutoWidth(true)
                .setFlexGrow(0);

        bottomGrid.addColumn(SeamMeasurementData::getLeftDownBottom)
                .setHeader("Left")
                .setAutoWidth(true);

        bottomGrid.addColumn(SeamMeasurementData::getRightDownBottom)
                .setHeader("Right")
                .setAutoWidth(true);

        bottomGrid.addColumn(SeamMeasurementData::getDiffDownBottom)
                .setHeader("Diff")
                .setAutoWidth(true)
                .setClassNameGenerator(this::diffClassForDownBottom);

        bottomGrid.getColumns().forEach(c -> c.setResizable(true));
        bottomGrid.setAllRowsVisible(true);
    }

    private String diffClassForUpTop(SeamMeasurementData data) {
        return AppliedTolerance.cssClassForDiff(data.getDiffUpTop(), ModelWrapper.getTolerance());
    }

    private String diffClassForDownBottom(SeamMeasurementData data) {
        return AppliedTolerance.cssClassForDiff(data.getDiffDownBottom(), ModelWrapper.getTolerance());
    }

    /**
     * Updates measurement values from the current model.
     */
    public void updateMeasurements() {
        // Ensure both providers share the same backing list (same SeamMeasurementData objects)
        seamMeasurementData.clear();
        seamMeasurementData.addAll(ModelWrapper.getSeamMeasurementData());

        // Both are backed by the same list; refresh both to update grids
        topData.refreshAll();
        bottomData.refreshAll();
    }
}
