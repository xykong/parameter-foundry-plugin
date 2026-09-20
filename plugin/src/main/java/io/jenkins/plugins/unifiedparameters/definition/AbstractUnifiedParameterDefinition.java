package io.jenkins.plugins.unifiedparameters.definition;

import hudson.model.Job;
import hudson.model.ParameterDefinition;
import hudson.model.ParametersDefinitionProperty;
import io.jenkins.plugins.unifiedparameters.Messages;
import io.jenkins.plugins.unifiedparameters.model.ChoiceMatrix;
import io.jenkins.plugins.unifiedparameters.model.Option;
import io.jenkins.plugins.unifiedparameters.renderer.TrustedRendererRegistry;
import io.jenkins.plugins.unifiedparameters.validation.DefinitionValidationService;
import java.io.Serial;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest2;

public abstract class AbstractUnifiedParameterDefinition extends ParameterDefinition {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final String PRESENCE_FIELD = "__unified_parameters_presence";
    public static final int NO_JAVASCRIPT_OPTION_LIMIT = 200;

    private List<Option> choices = List.of();
    private String renderer;
    private String providerId;
    private List<String> dependsOn = List.of();
    private String defaultMode = "none";
    private boolean required;
    private String environmentExport;
    private List<String> defaultValues = List.of();
    private int minSelections;
    // Boxed so that a definition stored before this field existed still means "unlimited".
    // XStream does not run field initializers, so a primitive would deserialize to 0.
    private Integer maxSelections;
    private String duplicatePolicy = "reject";
    private String standardApiInputMode = "repeated";
    private String delimiter = ",";
    private String selectedDisplay = "none";
    private ChoiceMatrix table;
    private int modelVersion = 1;

    protected AbstractUnifiedParameterDefinition(String name) {
        super(name);
    }

    public String getUiSearchPlaceholder() {
        return Messages.SearchOptions();
    }

    public String getUiSearchLabel() {
        return Messages.SearchOptions();
    }

    public String getUiSelectedOptionsLabel() {
        return Messages.SelectedOptions();
    }

    public String getUiAvailableOptionsLabel() {
        return Messages.AvailableOptions();
    }

    public String getUiSelectionSummaryTemplate() {
        return Messages.SelectedSummaryTemplate("{0}", "{1}");
    }

    public String getUiSelectionUpdated() {
        return Messages.SelectionUpdated();
    }

    public String getUiSelectGroup() {
        return Messages.SelectGroup();
    }

    public String getUiGroupSelectionUpdated() {
        return Messages.GroupSelectionUpdated();
    }

    public String getUiSelectionChanged() {
        return Messages.SelectionChanged("{0}", "{1}");
    }

    public String getUiRemovedTemplate() {
        return Messages.Removed("{0}");
    }

    public String getUiNoOptionsMatch() {
        return Messages.NoOptionsMatch();
    }

    public String getUiSelectVisible() {
        return Messages.SelectVisible();
    }

    public String getUiInvertVisible() {
        return Messages.InvertVisible();
    }

    public String getUiClearVisible() {
        return Messages.ClearVisible();
    }

    public String getUiClearAll() {
        return Messages.ClearAll();
    }

    public String getUiVisibleSelectionUpdated() {
        return Messages.VisibleSelectionUpdated();
    }

    public String getUiVisibleSelectionInverted() {
        return Messages.VisibleSelectionInverted();
    }

    public String getUiVisibleSelectionCleared() {
        return Messages.VisibleSelectionCleared();
    }

    public String getUiAllSelectionsCleared() {
        return Messages.AllSelectionsCleared("{0}");
    }

    public String getUiCustomValueSelected() {
        return Messages.CustomValueSelected("{0}");
    }

    public String getUiCustomValueInvalidWithRule() {
        return Messages.CustomValueInvalidWithRule("{0}");
    }

    public String getUiCustomValuePolicy() {
        return Messages.CustomValuePolicy();
    }

    public String getUiMatchesSummary() {
        return Messages.MatchesSummary("{0}");
    }

    public String getUiRadioDegraded() {
        return Messages.RadioDegraded("{0}");
    }

    public String getUiSelectAllCells() {
        return Messages.SelectAllCells();
    }

    public String getUiSelectRow() {
        return Messages.SelectRow("{0}");
    }

    public String getUiSelectColumn() {
        return Messages.SelectColumn("{0}");
    }

    public String getUiRowSelectionUpdated() {
        return Messages.RowSelectionUpdated("{0}");
    }

    public String getUiRowSelectionCleared() {
        return Messages.RowSelectionCleared("{0}");
    }

    public String getUiColumnSelectionUpdated() {
        return Messages.ColumnSelectionUpdated("{0}");
    }

    public String getUiColumnSelectionCleared() {
        return Messages.ColumnSelectionCleared("{0}");
    }

    public String getUiAllCellsSelected() {
        return Messages.AllCellsSelected();
    }

    public String getUiInvertSelection() {
        return Messages.InvertSelection();
    }

    public String getUiSelectionInverted() {
        return Messages.SelectionInverted("{0}");
    }

    public String getUiRowSelectionLimited() {
        return Messages.RowSelectionLimited("{0}");
    }

    public String getUiHideSelected() {
        return Messages.HideSelected();
    }

    public String getUiShowSelected() {
        return Messages.ShowSelected("{0}");
    }

    public String getUiLimitedSingleMatches() {
        return Messages.LimitedSingleMatches("{0}", "{1}");
    }

    public String getUiLimitedMatches() {
        return Messages.LimitedMatches("{0}", "{1}", "{2}");
    }

    public String getUiNoneOption() {
        return Messages.NoneOption();
    }

    public String getUiCustomOption() {
        return Messages.CustomOption("{0}");
    }

    public String getUiUseCustomValue() {
        return Messages.UseCustomValue("{0}");
    }

    public String getUiEnterCustomValue() {
        return Messages.EnterCustomValue();
    }

    public String getUiCustomValueHint() {
        return Messages.CustomValueHint();
    }

    public String getUiCustomValueInvalid() {
        return Messages.CustomValueInvalid();
    }

    public String getUiUseSearchAsCustomValue() {
        return Messages.UseSearchAsCustomValue();
    }

    public List<Option> getChoices() {
        return choices == null ? List.of() : choices;
    }

    @DataBoundSetter
    public void setChoices(List<Option> choices) {
        this.choices = choices == null ? List.of() : List.copyOf(new ArrayList<>(choices));
    }

    /**
     * Every machine value this parameter accepts: the flat options plus one option per
     * selectable matrix cell. Evaluation, validation, and the rendering fallback read this
     * set, while {@link #getChoices()} keeps the flat list the option editor owns.
     */
    public List<Option> getSelectableOptions() {
        List<Option> flat = getChoices();
        ChoiceMatrix matrix = getTable();
        if (matrix == null || matrix.getCells().isEmpty()) {
            return flat;
        }
        Map<String, Option> merged = new LinkedHashMap<>();
        for (Option option : flat) {
            if (option != null && option.getValue() != null) {
                merged.put(option.getValue(), option);
            }
        }
        for (var cell : matrix.getCells()) {
            if (cell == null || cell.getValue() == null) {
                continue;
            }
            Option option = new Option(cell.getValue());
            option.setLabel(matrix.labelFor(cell));
            option.setDescription(cell.getDescription());
            option.setDisabled(cell.isDisabled());
            option.setDisabledReason(cell.getDisabledReason());
            merged.put(cell.getValue(), option);
        }
        return List.copyOf(merged.values());
    }

    public String getRenderer() {
        return renderer == null || renderer.isBlank() ? getDefaultRenderer() : renderer;
    }

    @DataBoundSetter
    public void setRenderer(String renderer) {
        this.renderer = renderer;
    }

    public String getProviderId() {
        return providerId == null || providerId.isBlank() ? null : providerId;
    }

    @DataBoundSetter
    public void setProviderId(String providerId) {
        this.providerId = providerId == null || providerId.isBlank() ? null : providerId;
    }

    public boolean isProviderBacked() {
        return getProviderId() != null;
    }

    public List<String> getDependsOn() {
        return dependsOn == null ? List.of() : dependsOn;
    }

    @DataBoundSetter
    public void setDependsOn(List<String> dependsOn) {
        this.dependsOn = dependsOn == null ? List.of() : List.copyOf(new ArrayList<>(dependsOn));
    }

    public String getDependsOnText() {
        return getDependsOn().stream()
                .map(value -> value == null ? "" : value)
                .collect(java.util.stream.Collectors.joining(","));
    }

    public String getDefaultMode() {
        return defaultMode == null || defaultMode.isBlank() ? "none" : defaultMode;
    }

    @DataBoundSetter
    public void setDefaultMode(String defaultMode) {
        this.defaultMode = defaultMode;
    }

    public boolean isRequired() {
        return required;
    }

    @DataBoundSetter
    public void setRequired(boolean required) {
        this.required = required;
    }

    public String getEnvironmentExport() {
        return environmentExport == null || environmentExport.isBlank()
                ? getDefaultEnvironmentExport()
                : environmentExport;
    }

    @DataBoundSetter
    public void setEnvironmentExport(String environmentExport) {
        this.environmentExport = environmentExport;
    }

    public List<String> getDefaultValues() {
        return defaultValues == null ? List.of() : defaultValues;
    }

    @DataBoundSetter
    public void setDefaultValues(List<String> defaultValues) {
        this.defaultValues = defaultValues == null ? List.of() : List.copyOf(new ArrayList<>(defaultValues));
    }

    public int getMinSelections() {
        return minSelections;
    }

    @DataBoundSetter
    public void setMinSelections(int minSelections) {
        this.minSelections = minSelections;
    }

    public int getMaxSelections() {
        return maxSelections == null ? -1 : maxSelections;
    }

    @DataBoundSetter
    public void setMaxSelections(int maxSelections) {
        this.maxSelections = maxSelections;
    }

    public String getDuplicatePolicy() {
        return duplicatePolicy == null || duplicatePolicy.isBlank() ? "reject" : duplicatePolicy;
    }

    @DataBoundSetter
    public void setDuplicatePolicy(String duplicatePolicy) {
        this.duplicatePolicy = duplicatePolicy;
    }

    public String getStandardApiInputMode() {
        return standardApiInputMode == null || standardApiInputMode.isBlank() ? "repeated" : standardApiInputMode;
    }

    @DataBoundSetter
    public void setStandardApiInputMode(String standardApiInputMode) {
        this.standardApiInputMode = standardApiInputMode;
    }

    public String getDelimiter() {
        return delimiter == null || delimiter.isEmpty() ? "," : delimiter;
    }

    @DataBoundSetter
    public void setDelimiter(String delimiter) {
        this.delimiter = delimiter;
    }

    public String getSelectedDisplay() {
        return selectedDisplay == null || selectedDisplay.isBlank() ? "none" : selectedDisplay;
    }

    @DataBoundSetter
    public void setSelectedDisplay(String selectedDisplay) {
        this.selectedDisplay = selectedDisplay;
    }

    public ChoiceMatrix getTable() {
        return table;
    }

    @DataBoundSetter
    public void setTable(ChoiceMatrix table) {
        this.table = table;
    }

    public String getTableJson() {
        return table == null ? "" : JSONObject.fromObject(table).toString();
    }

    public String getDefaultValuesText() {
        return getDefaultValues().stream()
                .map(value -> value == null ? "" : value)
                .collect(java.util.stream.Collectors.joining("\n"));
    }

    public int getModelVersion() {
        return modelVersion;
    }

    public List<String> getConfigurationWarnings() {
        return DefinitionValidationService.validateStoredDefinition(this).getWarnings();
    }

    public Option findOption(String value) {
        return findOption(getSelectableOptions(), value);
    }

    public Option findOption(List<Option> options, String value) {
        for (Option option : options) {
            if (option != null && option.getValue() != null && option.getValue().equals(value)) {
                return option;
            }
        }
        return null;
    }

    public Option firstEnabledOption() {
        return firstEnabledOption(getSelectableOptions());
    }

    public Option firstEnabledOption(List<Option> options) {
        for (Option option : options) {
            if (option != null && !option.isDisabled()) {
                return option;
            }
        }
        return null;
    }

    public List<Option> getFallbackChoices() {
        List<Option> currentChoices = getSelectableOptions();
        if (currentChoices.size() <= NO_JAVASCRIPT_OPTION_LIMIT) {
            return currentChoices;
        }
        Map<String, Option> defaultsOutsideWindow = new LinkedHashMap<>();
        for (String defaultValue : getFallbackDefaultValues()) {
            Option option = findOption(defaultValue);
            if (option != null && currentChoices.indexOf(option) >= NO_JAVASCRIPT_OPTION_LIMIT) {
                defaultsOutsideWindow.put(defaultValue, option);
            }
        }
        int ordinaryLimit = Math.max(0, NO_JAVASCRIPT_OPTION_LIMIT - defaultsOutsideWindow.size());
        Map<String, Option> result = new LinkedHashMap<>();
        for (Option option : currentChoices.subList(0, ordinaryLimit)) {
            result.put(option.getValue(), option);
        }
        result.putAll(defaultsOutsideWindow);
        return List.copyOf(result.values());
    }

    public int getOptionCount() {
        return getSelectableOptions().size();
    }

    public List<String> getAvailableExternalRenderers() {
        return TrustedRendererRegistry.externalRendererIds(getRendererFamily());
    }

    public List<String> getRendererAdjuncts() {
        return TrustedRendererRegistry.trustedAdjuncts(getRenderer(), getRendererFamily());
    }

    public boolean isFallbackTruncated() {
        return getSelectableOptions().size() > NO_JAVASCRIPT_OPTION_LIMIT;
    }

    public boolean hasPresenceMarker(String[] markers) {
        if (markers == null) {
            return false;
        }
        for (String marker : markers) {
            if (getName().equals(marker)) {
                return true;
            }
        }
        return false;
    }

    public boolean isNoJavaScriptSubmitOwner() {
        StaplerRequest2 request = Stapler.getCurrentRequest2();
        if (request == null || !request.getRequestURI().matches(".*/build/?")) {
            return false;
        }
        ParametersDefinitionProperty property = request.findAncestorObject(ParametersDefinitionProperty.class);
        if (property == null) {
            Job<?, ?> job = request.findAncestorObject(Job.class);
            property = job == null ? null : job.getProperty(ParametersDefinitionProperty.class);
        }
        if (property == null) {
            return true;
        }
        for (ParameterDefinition definition : property.getParameterDefinitions()) {
            if (definition instanceof AbstractUnifiedParameterDefinition unified && unified.isProviderBacked()) {
                return false;
            }
        }
        for (ParameterDefinition definition : property.getParameterDefinitions()) {
            if (definition instanceof AbstractUnifiedParameterDefinition) {
                return definition.getName().equals(getName());
            }
        }
        return false;
    }

    protected abstract String getDefaultRenderer();

    protected abstract String getRendererFamily();

    protected abstract String getDefaultEnvironmentExport();

    protected abstract List<String> getFallbackDefaultValues();

    public abstract boolean isMultiple();
}
