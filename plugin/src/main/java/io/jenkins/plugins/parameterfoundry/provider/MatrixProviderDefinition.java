package io.jenkins.plugins.parameterfoundry.provider;

import hudson.Extension;
import hudson.model.Descriptor;
import io.jenkins.plugins.parameterfoundry.evaluation.ErrorCode;
import io.jenkins.plugins.parameterfoundry.model.AxisOption;
import io.jenkins.plugins.parameterfoundry.model.ChoiceMatrix;
import io.jenkins.plugins.parameterfoundry.model.MatrixCell;
import io.jenkins.plugins.parameterfoundry.model.Option;
import java.io.Serial;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

/**
 * Builds a matrix from two axes so a table can cross one governed list with another, for example
 * the registries of a Job against the agents that must log into them. Each axis is either an inline
 * list or the options of another Provider, which keeps the axes live: adding an agent updates the
 * columns of every Job that uses this Provider.
 */
public final class MatrixProviderDefinition extends SharedProviderDefinition implements MatrixProvider {

    @Serial
    private static final long serialVersionUID = 1L;

    private List<AxisOption> rows = List.of();
    private String rowsProviderId;
    private List<AxisOption> columns = List.of();
    private String columnsProviderId;
    private String cellValueTemplate = "{row}@{column}";
    private String cellLabelTemplate;
    private String rowSelectionMode = "any";
    private String rowAxisLabel;
    private String columnAxisLabel;

    @DataBoundConstructor
    public MatrixProviderDefinition(String id) {
        super(id);
    }

    public List<AxisOption> getRows() {
        return rows;
    }

    @DataBoundSetter
    public void setRows(List<AxisOption> rows) {
        this.rows = rows == null ? List.of() : List.copyOf(new ArrayList<>(rows));
    }

    /** {@code value|label} per line; the label is optional and defaults to the value. */
    public String getRowsText() {
        return axisText(getRows());
    }

    @DataBoundSetter
    public void setRowsText(String rowsText) {
        this.rows = parseAxis(rowsText);
    }

    public String getRowsProviderId() {
        return rowsProviderId;
    }

    @DataBoundSetter
    public void setRowsProviderId(String rowsProviderId) {
        this.rowsProviderId = rowsProviderId;
    }

    public List<AxisOption> getColumns() {
        return columns;
    }

    @DataBoundSetter
    public void setColumns(List<AxisOption> columns) {
        this.columns = columns == null ? List.of() : List.copyOf(new ArrayList<>(columns));
    }

    /** {@code value|label} per line; the label is optional and defaults to the value. */
    public String getColumnsText() {
        return axisText(getColumns());
    }

    @DataBoundSetter
    public void setColumnsText(String columnsText) {
        this.columns = parseAxis(columnsText);
    }

    public String getColumnsProviderId() {
        return columnsProviderId;
    }

    @DataBoundSetter
    public void setColumnsProviderId(String columnsProviderId) {
        this.columnsProviderId = columnsProviderId;
    }

    /**
     * Cell value built from the row and the column, with {@code {row}} and {@code {column}}
     * placeholders for the machine values and {@code {rowLabel}} and {@code {columnLabel}} for the
     * captions.
     */
    public String getCellValueTemplate() {
        return cellValueTemplate == null || cellValueTemplate.isBlank() ? "{row}@{column}" : cellValueTemplate;
    }

    @DataBoundSetter
    public void setCellValueTemplate(String cellValueTemplate) {
        this.cellValueTemplate = cellValueTemplate;
    }

    public String getCellLabelTemplate() {
        return cellLabelTemplate;
    }

    @DataBoundSetter
    public void setCellLabelTemplate(String cellLabelTemplate) {
        this.cellLabelTemplate = cellLabelTemplate;
    }

    /** {@code any}, {@code at-most-one}, or {@code exactly-one} cell per row. */
    public String getRowSelectionMode() {
        return rowSelectionMode == null || rowSelectionMode.isBlank() ? "any" : rowSelectionMode;
    }

    @DataBoundSetter
    public void setRowSelectionMode(String rowSelectionMode) {
        if (!Set.of("any", "at-most-one", "exactly-one").contains(rowSelectionMode)) {
            throw new IllegalArgumentException("Row selection mode must be any, at-most-one, or exactly-one.");
        }
        this.rowSelectionMode = rowSelectionMode;
    }

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

    @Override
    public ChoiceMatrix loadMatrix(ProviderLoadContext context) {
        context.checkCancelled();
        ProviderRequest request = context.request();
        List<AxisOption> rowAxis = axis(context, "row", getRows(), getRowsProviderId());
        List<AxisOption> columnAxis = axis(context, "column", getColumns(), getColumnsProviderId());
        if (rowAxis.isEmpty() || columnAxis.isEmpty()) {
            throw new ProviderException(
                    ErrorCode.PROVIDER_UNAVAILABLE,
                    getId(),
                    "A matrix Provider needs a non-empty row and column axis.");
        }
        ChoiceMatrix matrix = new ChoiceMatrix();
        matrix.setRows(rowAxis);
        matrix.setColumns(columnAxis);
        matrix.setRowSelectionMode(getRowSelectionMode());
        matrix.setRowAxisLabel(getRowAxisLabel());
        matrix.setColumnAxisLabel(getColumnAxisLabel());
        List<MatrixCell> cells = new ArrayList<>();
        Set<String> values = new LinkedHashSet<>();
        for (AxisOption row : rowAxis) {
            for (AxisOption column : columnAxis) {
                String value = render(getCellValueTemplate(), row, column, request);
                if (value == null || value.isBlank()) {
                    throw new ProviderException(
                            ErrorCode.PROVIDER_UNAVAILABLE, getId(), "A matrix cell value resolved to an empty value.");
                }
                if (!values.add(value)) {
                    throw new ProviderException(
                            ErrorCode.PROVIDER_UNAVAILABLE, getId(), "Duplicate matrix cell value: " + value);
                }
                MatrixCell cell = new MatrixCell(row.getValue(), column.getValue(), value);
                String label = render(getCellLabelTemplate(), row, column, request);
                if (label != null && !label.isBlank()) {
                    cell.setLabel(label);
                }
                cells.add(cell);
            }
        }
        matrix.setCells(cells);
        return matrix;
    }

    @Override
    public List<Option> load(ProviderLoadContext context) {
        return loadMatrix(context).selectableOptions();
    }

    private List<AxisOption> axis(
            ProviderLoadContext context, String axisName, List<AxisOption> inline, String providerId) {
        if (!inline.isEmpty()) {
            return inline;
        }
        if (providerId == null || providerId.isBlank()) {
            throw new ProviderException(
                    ErrorCode.PROVIDER_UNAVAILABLE,
                    getId(),
                    "The " + axisName + " axis needs an inline list or a Provider id.");
        }
        ProviderRequest request = context.request();
        if (providerId.equals(request.providerId())) {
            throw new ProviderException(
                    ErrorCode.PROVIDER_UNAVAILABLE,
                    getId(),
                    "A matrix Provider cannot be its own " + axisName + " axis.");
        }
        ProviderRegistry.LocatedProvider located = ProviderRegistry.findProvider(request.job(), providerId)
                .orElseThrow(() -> new ProviderException(
                        ErrorCode.PROVIDER_UNAVAILABLE,
                        getId(),
                        "The " + axisName + " Provider is not visible to this Job: " + providerId));
        ProviderResolution resolution = ProviderRuntime.get()
                .resolve(request.job(), located, request.operation(), request.dependencyValues(), request.principal());
        List<AxisOption> axis = new ArrayList<>();
        for (Option option : resolution.options()) {
            axis.add(new AxisOption(option.getValue(), option.getLabel()));
        }
        return List.copyOf(axis);
    }

    private static String axisText(List<AxisOption> axis) {
        List<String> lines = new ArrayList<>();
        for (AxisOption option : axis) {
            lines.add(option.getValue() + "|" + (option.getLabel() == null ? "" : option.getLabel()));
        }
        return String.join("\n", lines);
    }

    private static List<AxisOption> parseAxis(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<AxisOption> axis = new ArrayList<>();
        for (String rawLine : text.split("\r?\n")) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }
            int separator = line.indexOf('|');
            String value = separator < 0 ? line : line.substring(0, separator).trim();
            String label = separator < 0 ? null : line.substring(separator + 1).trim();
            if (!value.isEmpty()) {
                axis.add(new AxisOption(value, label == null || label.isEmpty() ? value : label));
            }
        }
        return List.copyOf(axis);
    }

    private static String render(String template, AxisOption row, AxisOption column, ProviderRequest request) {
        if (template == null) {
            return null;
        }
        return template.replace("{row}", String.valueOf(row.getValue()))
                .replace("{rowLabel}", String.valueOf(ChoiceMatrix.caption(row)))
                .replace("{column}", String.valueOf(column.getValue()))
                .replace("{columnLabel}", String.valueOf(ChoiceMatrix.caption(column)));
    }

    @Override
    protected String providerFingerprint() {
        return Digests.sha256(String.join(
                "\u0000",
                "matrix",
                Digests.axis(rows),
                String.valueOf(rowsProviderId),
                Digests.axis(columns),
                String.valueOf(columnsProviderId),
                getCellValueTemplate(),
                String.valueOf(getCellLabelTemplate()),
                getRowSelectionMode(),
                String.valueOf(getRowAxisLabel()),
                String.valueOf(getColumnAxisLabel())));
    }

    @Extension
    @Symbol("foundryMatrixProvider")
    public static final class DescriptorImpl extends Descriptor<SharedProviderDefinition> {
        @Override
        public String getDisplayName() {
            return "Parameter Foundry Matrix Provider";
        }
    }
}
