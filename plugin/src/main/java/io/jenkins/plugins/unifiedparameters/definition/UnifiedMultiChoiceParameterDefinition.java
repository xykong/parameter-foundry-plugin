package io.jenkins.plugins.unifiedparameters.definition;

import hudson.AbortException;
import hudson.Extension;
import hudson.cli.CLICommand;
import hudson.model.Descriptor;
import hudson.model.Job;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.model.StringParameterValue;
import io.jenkins.plugins.unifiedparameters.evaluation.DecodedInput;
import io.jenkins.plugins.unifiedparameters.evaluation.EvaluationException;
import io.jenkins.plugins.unifiedparameters.evaluation.InputDecoders;
import io.jenkins.plugins.unifiedparameters.evaluation.ParameterEvaluationService;
import io.jenkins.plugins.unifiedparameters.model.OptionImportEndpoint;
import io.jenkins.plugins.unifiedparameters.model.ChoiceMatrix;
import io.jenkins.plugins.unifiedparameters.provider.ProviderOperation;
import io.jenkins.plugins.unifiedparameters.snapshot.ProviderSnapshotMetadata;
import io.jenkins.plugins.unifiedparameters.snapshot.ProviderSubmission;
import io.jenkins.plugins.unifiedparameters.snapshot.ResolvedProviderOptions;
import io.jenkins.plugins.unifiedparameters.validation.DefinitionValidationResult;
import io.jenkins.plugins.unifiedparameters.validation.DefinitionValidationService;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;
import org.kohsuke.stapler.verb.POST;

public final class UnifiedMultiChoiceParameterDefinition extends AbstractUnifiedParameterDefinition {

    private static final long serialVersionUID = 1L;

    private List<String> defaultValues = List.of();
    private int minSelections;
    private int maxSelections = -1;
    private String duplicatePolicy = "reject";
    private String standardApiInputMode = "repeated";
    private String delimiter = ",";
    private String selectedDisplay = "none";
    private ChoiceMatrix table;

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

    public String getSelectedDisplay() {
        return selectedDisplay == null || selectedDisplay.isBlank() ? "none" : selectedDisplay;
    }

    @DataBoundSetter
    public void setSelectedDisplay(String selectedDisplay) {
        this.selectedDisplay = selectedDisplay;
    }

    @DataBoundConstructor
    public UnifiedMultiChoiceParameterDefinition(String name) {
        super(name);
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
        return maxSelections;
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

    @Override
    public ParameterValue createValue(StaplerRequest2 request) {
        try {
            ResolvedProviderOptions resolved = optionsFor(request, ProviderOperation.BUILD, null);
            return toValue(
                    ParameterEvaluationService.evaluateMultiple(
                            this, InputDecoders.fromStandardRequest(this, request), resolved.options()),
                    resolved.metadata());
        } catch (EvaluationException exception) {
            throw HttpResponses.errorWithoutStack(400, exception.getSafeMessage());
        }
    }

    @Override
    public ParameterValue createValue(StaplerRequest2 request, JSONObject submitted) {
        try {
            ResolvedProviderOptions resolved = optionsFor(request, ProviderOperation.BUILD, submitted);
            return toValue(
                    ParameterEvaluationService.evaluateMultiple(
                            this, InputDecoders.fromSubmittedJson(this, submitted), resolved.options()),
                    resolved.metadata());
        } catch (EvaluationException exception) {
            throw HttpResponses.errorWithoutStack(400, exception.getSafeMessage());
        }
    }

    @Override
    public ParameterValue createValue(CLICommand command, String value) throws IOException, InterruptedException {
        try {
            ResolvedProviderOptions resolved = isProviderBacked()
                    ? ProviderSubmission.resolveForCli(
                            ProviderSubmission.requireOwningJob(this), this, ProviderOperation.BUILD, command)
                    : new ResolvedProviderOptions(getChoices(), null, null, false);
            return toValue(
                    ParameterEvaluationService.evaluateMultiple(
                            this, InputDecoders.fromCli(this, value), resolved.options()),
                    resolved.metadata());
        } catch (EvaluationException exception) {
            throw new AbortException(exception.getSafeMessage());
        }
    }

    @Override
    public UnifiedMultiChoiceParameterValue getDefaultParameterValue() {
        try {
            ResolvedProviderOptions resolved = ProviderSubmission.resolveForDefault(this);
            return toValue(
                    ParameterEvaluationService.evaluateMultiple(this, DecodedInput.absent(), resolved.options()),
                    resolved.metadata());
        } catch (EvaluationException exception) {
            return null;
        }
    }

    @Override
    public boolean isValid(ParameterValue value) {
        List<String> submitted;
        if (value instanceof UnifiedMultiChoiceParameterValue multiValue) {
            submitted = multiValue.getValue();
        } else if (value instanceof StringParameterValue stringValue) {
            try {
                submitted = InputDecoders.fromCli(this, stringValue.getValue()).values();
            } catch (EvaluationException exception) {
                return false;
            }
        } else {
            return false;
        }
        try {
            ResolvedProviderOptions resolved = optionsForOwner(ProviderOperation.VALIDATE);
            ParameterEvaluationService.evaluateMultiple(this, DecodedInput.present(submitted), resolved.options());
            return true;
        } catch (EvaluationException exception) {
            return false;
        }
    }

    @Override
    public ParameterDefinition copyWithDefaultValue(ParameterValue defaultValue) {
        if (!(defaultValue instanceof UnifiedMultiChoiceParameterValue multiValue)) {
            return this;
        }
        UnifiedMultiChoiceParameterDefinition copy = copyDefinition();
        copy.setDefaultMode("explicit");
        copy.setDefaultValues(multiValue.getValue());
        return copy;
    }

    public boolean isDefault(String value) {
        try {
            ResolvedProviderOptions resolved = optionsForOwner(ProviderOperation.RESOLVE);
            return ParameterEvaluationService.evaluateMultiple(this, DecodedInput.absent(), resolved.options())
                    .contains(value);
        } catch (EvaluationException exception) {
            return false;
        }
    }

    public String getDefaultValuesText() {
        return getDefaultValues().stream()
                .map(value -> value == null ? "" : value)
                .collect(java.util.stream.Collectors.joining("\n"));
    }

    @Override
    protected String getDefaultRenderer() {
        return "checkbox";
    }

    @Override
    protected String getRendererFamily() {
        return io.jenkins.plugins.unifiedparameters.renderer.TrustedRendererRegistry.FAMILY_MULTI_CHOICE;
    }

    @Override
    protected String getDefaultEnvironmentExport() {
        return "delimited";
    }

    @Override
    protected List<String> getFallbackDefaultValues() {
        try {
            ResolvedProviderOptions resolved = optionsForOwner(ProviderOperation.RESOLVE);
            return ParameterEvaluationService.evaluateMultiple(this, DecodedInput.absent(), resolved.options());
        } catch (EvaluationException exception) {
            return List.of();
        }
    }

    @Override
    public boolean isMultiple() {
        return true;
    }

    private UnifiedMultiChoiceParameterDefinition copyDefinition() {
        UnifiedMultiChoiceParameterDefinition copy = new UnifiedMultiChoiceParameterDefinition(getName());
        copy.setDescription(getDescription());
        copy.setChoices(getChoices());
        copy.setProviderId(getProviderId());
        copy.setDependsOn(getDependsOn());
        copy.setRenderer(getRenderer());
        copy.setSelectedDisplay(getSelectedDisplay());
        copy.setTable(getTable());
        copy.setDefaultMode(getDefaultMode());
        copy.setDefaultValues(getDefaultValues());
        copy.setRequired(isRequired());
        copy.setEnvironmentExport(getEnvironmentExport());
        copy.setMinSelections(getMinSelections());
        copy.setMaxSelections(getMaxSelections());
        copy.setDuplicatePolicy(getDuplicatePolicy());
        copy.setStandardApiInputMode(getStandardApiInputMode());
        copy.setDelimiter(getDelimiter());
        return copy;
    }

    private ResolvedProviderOptions optionsFor(
            StaplerRequest2 request, ProviderOperation operation, JSONObject submitted) {
        if (!isProviderBacked()) {
            return new ResolvedProviderOptions(getChoices(), null, null, false);
        }
        Job<?, ?> job = request.findAncestorObject(Job.class);
        if (job == null) {
            return optionsForOwner(operation);
        }
        return ProviderSubmission.resolve(job, this, operation, request, submitted);
    }

    private ResolvedProviderOptions optionsForOwner(ProviderOperation operation) {
        if (!isProviderBacked()) {
            return new ResolvedProviderOptions(getChoices(), null, null, false);
        }
        return ProviderSubmission.resolveWithoutSubmission(ProviderSubmission.requireOwningJob(this), this, operation);
    }

    private UnifiedMultiChoiceParameterValue toValue(List<String> values, ProviderSnapshotMetadata metadata) {
        return new UnifiedMultiChoiceParameterValue(
                getName(), values, getDescription(), getEnvironmentExport(), getDelimiter(), metadata);
    }

    @Extension
    @Symbol("unifiedMultiChoice")
    public static final class DescriptorImpl extends ParameterDescriptor {
        @POST
        public void doPreviewImport(StaplerRequest2 request, StaplerResponse2 response) throws IOException {
            OptionImportEndpoint.previewForAdministrator(request, response);
        }

        @Override
        public UnifiedMultiChoiceParameterDefinition newInstance(StaplerRequest2 request, JSONObject formData)
                throws Descriptor.FormException {
            JSONObject data = JSONObject.fromObject(formData);
            String defaultValuesText = data.optString("defaultValuesText", "");
            String dependsOnText = data.optString("dependsOnText", "");
            data.remove("defaultValuesText");
            data.remove("dependsOnText");
            UnifiedMultiChoiceParameterDefinition definition =
                    (UnifiedMultiChoiceParameterDefinition) super.newInstance(request, data);
            definition.setDefaultValues(Arrays.stream(defaultValuesText.split("[\\r\\n,]+"))
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .toList());
            definition.setDependsOn(Arrays.stream(dependsOnText.split("[\\r\\n,]+"))
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .toList());
            DefinitionValidationResult validation = DefinitionValidationService.validateStoredDefinition(definition);
            if (!validation.isValid()) {
                throw new Descriptor.FormException(validation.summarize(), "parameter");
            }
            return definition;
        }

        @Override
        public String getDisplayName() {
            return "Unified Multi-choice Parameter";
        }
    }
}
