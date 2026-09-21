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
    private String rowAxisLabel;
    private String columnAxisLabel;

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

    /**
     * Name of the row axis and of the column axis. A table migrated from two choice parameters shows
     * the source parameter names here, so the grid reads as "these rows against these columns"
     * without repeating the parameter names in every label.
     */
    public String getRowAxisLabel() {
        return rowAxisLabel;
    }

    @DataBoundSetter
    public void setRowAxisLabel(String rowAxisLabel) {
        this.rowAxisLabel = rowAxisLabel;
    }

    public String getColumnAxisLabel() {
        return columnAxisLabel;
    }

    @DataBoundSetter
    public void setColumnAxisLabel(String columnAxisLabel) {
        this.columnAxisLabel = columnAxisLabel;
    }

    public AxisOption findRow(String value) {
        return find(rows, value);
    }

    public AxisOption findColumn(String value) {
        return find(columns, value);
    }

    /** Label of a cell: its own label when set, otherwise the row and column captions. */
    public String labelFor(MatrixCell cell) {
        if (cell == null) {
            return "";
        }
        if (cell.getLabel() != null && !cell.getLabel().equals(cell.getValue())) {
            return cell.getLabel();
        }
        return caption(findRow(cell.getRow())) + " / " + caption(findColumn(cell.getColumn()));
    }

    /** Every selectable value of the matrix, in row-major order. */
    public List<Option> selectableOptions() {
        List<Option> options = new ArrayList<>();
        for (MatrixCell cell : cells) {
            if (cell == null || cell.getValue() == null) {
                continue;
            }
            Option option = new Option(cell.getValue());
            option.setLabel(labelFor(cell));
            option.setDescription(cell.getDescription());
            option.setDisabled(cell.isDisabled());
            option.setDisabledReason(cell.getDisabledReason());
            options.add(option);
        }
        return List.copyOf(options);
    }

    public static String caption(AxisOption option) {
        if (option == null) {
            return "";
        }
        return option.getLabel() == null || option.getLabel().isBlank() ? option.getValue() : option.getLabel();
    }

    private static AxisOption find(List<AxisOption> axis, String value) {
        for (AxisOption option : axis) {
            if (option != null && option.getValue() != null && option.getValue().equals(value)) {
                return option;
            }
        }
        return null;
    }
}
