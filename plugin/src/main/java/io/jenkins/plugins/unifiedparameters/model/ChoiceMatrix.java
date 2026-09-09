package io.jenkins.plugins.unifiedparameters.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

public final class ChoiceMatrix implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private List<AxisOption> rows = List.of();
    private List<AxisOption> columns = List.of();
    private List<MatrixCell> cells = List.of();
    private String rowSelectionMode = "any";

    @DataBoundConstructor
    public ChoiceMatrix() {}

    public List<AxisOption> getRows() {
        return rows;
    }

    @DataBoundSetter
    public void setRows(List<AxisOption> rows) {
        this.rows = rows == null ? List.of() : List.copyOf(new ArrayList<>(rows));
    }

    public List<AxisOption> getColumns() {
        return columns;
    }

    @DataBoundSetter
    public void setColumns(List<AxisOption> columns) {
        this.columns = columns == null ? List.of() : List.copyOf(new ArrayList<>(columns));
    }

    public List<MatrixCell> getCells() {
        return cells;
    }

    @DataBoundSetter
    public void setCells(List<MatrixCell> cells) {
        this.cells = cells == null ? List.of() : List.copyOf(new ArrayList<>(cells));
    }

    public String getRowSelectionMode() {
        return rowSelectionMode == null || rowSelectionMode.isBlank() ? "any" : rowSelectionMode;
    }

    @DataBoundSetter
    public void setRowSelectionMode(String rowSelectionMode) {
        this.rowSelectionMode = rowSelectionMode;
    }
}
