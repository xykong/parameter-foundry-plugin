package io.jenkins.plugins.parameterfoundry.definition;

import hudson.AbortException;
import hudson.Extension;
import hudson.cli.CLICommand;
import hudson.model.Descriptor;
import hudson.model.Job;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.model.StringParameterValue;
import io.jenkins.plugins.parameterfoundry.evaluation.DecodedInput;
import io.jenkins.plugins.parameterfoundry.evaluation.EvaluationException;
import io.jenkins.plugins.parameterfoundry.evaluation.ExtendedChoiceDefaultCompatibility;
import io.jenkins.plugins.parameterfoundry.evaluation.GitBranchInputCompatibility;
import io.jenkins.plugins.parameterfoundry.evaluation.InputDecoders;
import io.jenkins.plugins.parameterfoundry.evaluation.ParameterEvaluationService;
import io.jenkins.plugins.parameterfoundry.model.Option;
import io.jenkins.plugins.parameterfoundry.model.OptionImportEndpoint;
import io.jenkins.plugins.parameterfoundry.provider.ProviderOperation;
import io.jenkins.plugins.parameterfoundry.snapshot.ProviderSnapshotMetadata;
import io.jenkins.plugins.parameterfoundry.snapshot.ProviderSubmission;
import io.jenkins.plugins.parameterfoundry.snapshot.ResolvedProviderOptions;
import io.jenkins.plugins.parameterfoundry.validation.DefinitionValidationResult;
import io.jenkins.plugins.parameterfoundry.validation.DefinitionValidationService;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;
import org.kohsuke.stapler.verb.POST;

public final class FoundryChoiceParameterDefinition extends AbstractFoundryParameterDefinition {

    private static final long serialVersionUID = 1L;

    private String selectionMode = "single";
    private String defaultValue;
    private String legacyDefaultLiteral;
    private String gitSelectionMode = "default";
    private transient boolean gitRebuildInput;
    private String environmentExportOrder = "canonical";
    private boolean allowCustomValue;
    private String customValuePattern;
    private int customValueMaxLength = 256;

    @DataBoundConstructor
    public FoundryChoiceParameterDefinition(String name) {
        super(name);
    }

    public String getSelectionMode() {
        return selectionMode == null || selectionMode.isBlank() ? "single" : selectionMode;
    }

    @DataBoundSetter
    public void setSelectionMode(String selectionMode) {
        this.selectionMode = selectionMode;
    }

    /**
     * {@code canonical} exports the sorted canonical set (the default). {@code submitted} exports
     * the values in the order the caller submitted them, which keeps the environment variable of a
     * migrated Job byte-identical to the source control.
     */
    public String getEnvironmentExportOrder() {
        return environmentExportOrder == null || environmentExportOrder.isBlank()
                ? "canonical"
                : environmentExportOrder;
    }

    @DataBoundSetter
    public void setEnvironmentExportOrder(String environmentExportOrder) {
        if (!java.util.Set.of("canonical", "submitted").contains(environmentExportOrder)) {
            throw new IllegalArgumentException("Environment export order must be canonical or submitted.");
        }
        this.environmentExportOrder = environmentExportOrder;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public String getLegacyDefaultLiteral() {
        return legacyDefaultLiteral;
    }

    @DataBoundSetter
    public void setLegacyDefaultLiteral(String literal) {
        this.legacyDefaultLiteral = literal == null || literal.isBlank() ? null : literal;
    }

    @DataBoundSetter
    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    /** Source dropdown selection is independent of the API/CLI default. */
    public String getGitSelectionMode() {
        return gitSelectionMode == null ? "default" : gitSelectionMode;
    }

    @DataBoundSetter
    public void setGitSelectionMode(String gitSelectionMode) {
        this.gitSelectionMode = gitSelectionMode;
    }

    public boolean isGitRebuildInput() {
        return gitRebuildInput && GitBranchInputCompatibility.enabled(this);
    }

    public boolean isAllowCustomValue() {
        return allowCustomValue;
    }

    @DataBoundSetter
    public void setAllowCustomValue(boolean allowCustomValue) {
        this.allowCustomValue = allowCustomValue;
    }

    public String getCustomValuePattern() {
        return customValuePattern;
    }

    @DataBoundSetter
    public void setCustomValuePattern(String customValuePattern) {
        this.customValuePattern =
                customValuePattern == null || customValuePattern.isBlank() ? null : customValuePattern;
    }

    public int getCustomValueMaxLength() {
        return customValueMaxLength;
    }

    @DataBoundSetter
    public void setCustomValueMaxLength(int customValueMaxLength) {
        this.customValueMaxLength = customValueMaxLength;
    }

    @Override
    public List<Option> getFallbackChoices() {
        List<Option> fallback = new ArrayList<>(super.getFallbackChoices());
        if (!"explicit".equals(getDefaultMode())
                || !allowCustomValue
                || defaultValue == null
                || findOption(defaultValue) != null
                || fallback.stream().anyMatch(option -> defaultValue.equals(option.getValue()))) {
            return List.copyOf(fallback);
        }
        Option historical = new Option(defaultValue);
        historical.setLabel(defaultValue + " (custom)");
        if (fallback.size() >= NO_JAVASCRIPT_OPTION_LIMIT) {
            fallback.set(NO_JAVASCRIPT_OPTION_LIMIT - 1, historical);
        } else {
            fallback.add(historical);
        }
        return List.copyOf(fallback);
    }

    @Override
    public ParameterValue createValue(StaplerRequest2 request) {
        try {
            DecodedInput input = InputDecoders.fromStandardRequest(this, request);
            if (!input.present() && ExtendedChoiceDefaultCompatibility.enabled(this)) {
                return ExtendedChoiceDefaultCompatibility.evaluate(this);
            }
            ResolvedProviderOptions resolved = optionsFor(request, ProviderOperation.BUILD, null, input);
            if (isMultiple()) {
                List<String> canonical =
                        ParameterEvaluationService.evaluateMultiple(this, input, resolved.options(), resolved.matrix());
                return toMultiValue(canonical, submittedOrder(canonical, input.values()), resolved.metadata());
            }
            return toValue(
                    ParameterEvaluationService.evaluateSingle(this, input, resolved.options()), resolved.metadata());
        } catch (EvaluationException exception) {
            throw HttpResponses.errorWithoutStack(400, exception.getSafeMessage());
        }
    }

    @Override
    public ParameterValue createValue(StaplerRequest2 request, JSONObject submitted) {
        try {
            DecodedInput input = InputDecoders.fromSubmittedJson(this, submitted);
            ResolvedProviderOptions resolved = optionsFor(request, ProviderOperation.BUILD, submitted, input);
            if (isMultiple()) {
                List<String> canonical =
                        ParameterEvaluationService.evaluateMultiple(this, input, resolved.options(), resolved.matrix());
                return toMultiValue(canonical, submittedOrder(canonical, input.values()), resolved.metadata());
            }
            return toValue(
                    ParameterEvaluationService.evaluateSingle(this, input, resolved.options()), resolved.metadata());
        } catch (EvaluationException exception) {
            throw HttpResponses.errorWithoutStack(400, exception.getSafeMessage());
        }
    }

    @Override
    public ParameterValue createValue(CLICommand command, String value) throws IOException, InterruptedException {
        try {
            DecodedInput input = InputDecoders.fromCli(this, value);
            if (!input.present() && ExtendedChoiceDefaultCompatibility.enabled(this)) {
                return ExtendedChoiceDefaultCompatibility.evaluate(this);
            }
            ResolvedProviderOptions resolved = !needsOptions(input)
                    ? new ResolvedProviderOptions(List.of(), null, null, null, false)
                    : isProviderBacked()
                            ? ProviderSubmission.resolveForCli(
                                    ProviderSubmission.requireOwningJob(this), this, ProviderOperation.BUILD, command)
                            : new ResolvedProviderOptions(getSelectableOptions(), getTable(), null, null, false);
            if (isMultiple()) {
                List<String> canonical =
                        ParameterEvaluationService.evaluateMultiple(this, input, resolved.options(), resolved.matrix());
                return toMultiValue(canonical, submittedOrder(canonical, input.values()), resolved.metadata());
            }
            return toValue(
                    ParameterEvaluationService.evaluateSingle(this, input, resolved.options()), resolved.metadata());
        } catch (EvaluationException exception) {
            throw new AbortException(exception.getSafeMessage());
        }
    }

    @Override
    public ParameterValue getDefaultParameterValue() {
        try {
            if (ExtendedChoiceDefaultCompatibility.enabled(this)) {
                return ExtendedChoiceDefaultCompatibility.evaluate(this);
            }
            ResolvedProviderOptions resolved = needsOptions(DecodedInput.absent())
                    ? ProviderSubmission.resolveForDefault(this)
                    : new ResolvedProviderOptions(List.of(), null, null, null, false);
            if (isMultiple()) {
                return toMultiValue(
                        ParameterEvaluationService.evaluateMultiple(
                                this, DecodedInput.absent(), resolved.options(), resolved.matrix()),
                        resolved.metadata());
            }
            String value = ParameterEvaluationService.evaluateSingle(this, DecodedInput.absent(), resolved.options());
            if (value == null && isRequired()) {
                return null;
            }
            return toValue(value, resolved.metadata());
        } catch (EvaluationException exception) {
            return null;
        }
    }

    @Override
    public boolean isValid(ParameterValue value) {
        try {
            if (value instanceof FoundryChoiceParameterValue choice
                    && choice.getLegacyDefaultLiteral() != null
                    && !ExtendedChoiceDefaultCompatibility.enabled(this)) {
                return false;
            }
            if (GitBranchInputCompatibility.enabled(this)) {
                DefinitionValidationService.requireValid(this);
                if (value instanceof FoundryChoiceParameterValue choiceValue) {
                    return !choiceValue.isMultiple();
                }
                return value instanceof StringParameterValue;
            }
            ResolvedProviderOptions resolved = optionsForOwner(ProviderOperation.VALIDATE);
            if (value instanceof FoundryChoiceParameterValue choiceValue) {
                if (isMultiple()) {
                    ParameterEvaluationService.evaluateMultiple(
                            this, DecodedInput.present(choiceValue.getValues()), resolved.options(), resolved.matrix());
                } else {
                    String submitted = choiceValue.getScalarValue();
                    ParameterEvaluationService.evaluateSingle(
                            this,
                            submitted == null
                                    ? DecodedInput.present(List.of())
                                    : DecodedInput.present(List.of(submitted)),
                            resolved.options());
                }
                return true;
            }
            if (!(value instanceof StringParameterValue stringValue)) {
                return false;
            }
            if (isMultiple()) {
                ParameterEvaluationService.evaluateMultiple(
                        this,
                        DecodedInput.present(InputDecoders.fromCli(this, stringValue.getValue())
                                .values()),
                        resolved.options(),
                        resolved.matrix());
            } else {
                String submitted = stringValue.getValue();
                ParameterEvaluationService.evaluateSingle(
                        this,
                        submitted == null ? DecodedInput.present(List.of()) : DecodedInput.present(List.of(submitted)),
                        resolved.options());
            }
            return true;
        } catch (EvaluationException exception) {
            return false;
        }
    }

    @Override
    public ParameterDefinition copyWithDefaultValue(ParameterValue defaultValue) {
        if (!(defaultValue instanceof FoundryChoiceParameterValue choiceValue)) {
            return this;
        }
        FoundryChoiceParameterDefinition copy = copyDefinition();
        copy.gitRebuildInput = GitBranchInputCompatibility.enabled(this);
        copy.setDefaultMode("explicit");
        if (isMultiple()) {
            copy.setDefaultValues(choiceValue.getValues());
            copy.setLegacyDefaultLiteral(choiceValue.getLegacyDefaultLiteral());
        } else {
            copy.setDefaultValue(choiceValue.getScalarValue());
        }
        return copy;
    }

    public boolean isDefault(String value) {
        if (isMultiple()) {
            try {
                ResolvedProviderOptions resolved = optionsForOwner(ProviderOperation.RESOLVE);
                return ParameterEvaluationService.evaluateMultiple(
                                this, DecodedInput.absent(), resolved.options(), resolved.matrix())
                        .contains(value);
            } catch (EvaluationException exception) {
                return false;
            }
        }
        ParameterValue defaultParameterValue = getDefaultParameterValue();
        return defaultParameterValue instanceof FoundryChoiceParameterValue choiceValue
                && java.util.Objects.equals(choiceValue.getValue(), value);
    }

    @Override
    protected String getDefaultRenderer() {
        return isMultiple() ? "checkbox" : "select";
    }

    @Override
    protected String getRendererFamily() {
        return isMultiple()
                ? io.jenkins.plugins.parameterfoundry.renderer.TrustedRendererRegistry.FAMILY_MULTI_CHOICE
                : io.jenkins.plugins.parameterfoundry.renderer.TrustedRendererRegistry.FAMILY_SINGLE_CHOICE;
    }

    @Override
    protected String getDefaultEnvironmentExport() {
        return isMultiple() ? "delimited" : "string";
    }

    @Override
    protected java.util.List<String> getFallbackDefaultValues() {
        if (isMultiple()) {
            try {
                ResolvedProviderOptions resolved = optionsForOwner(ProviderOperation.RESOLVE);
                return ParameterEvaluationService.evaluateMultiple(
                        this, DecodedInput.absent(), resolved.options(), resolved.matrix());
            } catch (EvaluationException exception) {
                return List.of();
            }
        }
        ParameterValue value = getDefaultParameterValue();
        return value instanceof FoundryChoiceParameterValue choiceValue ? choiceValue.getValues() : java.util.List.of();
    }

    @Override
    public boolean isMultiple() {
        return "multiple".equals(getSelectionMode());
    }

    private FoundryChoiceParameterDefinition copyDefinition() {
        FoundryChoiceParameterDefinition copy = new FoundryChoiceParameterDefinition(getName());
        copy.setSelectionMode(getSelectionMode());
        copy.setDescription(getDescription());
        copy.setChoices(getChoices());
        copy.setProviderId(getProviderId());
        copy.setDependsOn(getDependsOn());
        copy.setRenderer(getRenderer());
        copy.setDefaultMode(getDefaultMode());
        copy.setDefaultValue(getDefaultValue());
        copy.setLegacyDefaultLiteral(getLegacyDefaultLiteral());
        copy.setEnvironmentExportOrder(getEnvironmentExportOrder());
        copy.setGitSelectionMode(getGitSelectionMode());
        copy.setDefaultValues(getDefaultValues());
        copy.setMinSelections(getMinSelections());
        copy.setMaxSelections(getMaxSelections());
        copy.setDuplicatePolicy(getDuplicatePolicy());
        copy.setStandardApiInputMode(getStandardApiInputMode());
        copy.setDelimiter(getDelimiter());
        copy.setSelectedDisplay(getSelectedDisplay());
        copy.setTable(getTable());
        copy.setAllowCustomValue(isAllowCustomValue());
        copy.setCustomValuePattern(getCustomValuePattern());
        copy.setCustomValueMaxLength(getCustomValueMaxLength());
        copy.setRequired(isRequired());
        copy.setEnvironmentExport(getEnvironmentExport());
        return copy;
    }

    private ResolvedProviderOptions optionsFor(
            StaplerRequest2 request, ProviderOperation operation, JSONObject submitted, DecodedInput input) {
        if (!isProviderBacked()) {
            return new ResolvedProviderOptions(getSelectableOptions(), getTable(), null, null, false);
        }
        if (request == null
                && !needsOptions(input)
                && (submitted == null || submitted.optString("snapshotId", "").isBlank())) {
            return new ResolvedProviderOptions(List.of(), null, null, null, false);
        }
        Job<?, ?> job = request == null ? null : request.findAncestorObject(Job.class);
        if (job == null) {
            job = ProviderSubmission.requireOwningJob(this);
        }
        if (request == null) {
            return io.jenkins.plugins.parameterfoundry.snapshot.ProviderSnapshotStore.get()
                    .resolveForBuild(
                            job,
                            this,
                            java.util.Map.of(),
                            submitted == null ? null : submitted.optString("snapshotId", null),
                            operation,
                            needsOptions(input));
        }
        return ProviderSubmission.resolve(job, this, operation, request, submitted, needsOptions(input));
    }

    public boolean needsOptions(DecodedInput input) {
        return !GitBranchInputCompatibility.enabled(this) || GitBranchInputCompatibility.requiresOptions(this, input);
    }

    private ResolvedProviderOptions optionsForOwner(ProviderOperation operation) {
        if (!isProviderBacked()) {
            return new ResolvedProviderOptions(getSelectableOptions(), getTable(), null, null, false);
        }
        return ProviderSubmission.resolveWithoutSubmission(ProviderSubmission.requireOwningJob(this), this, operation);
    }

    private FoundryChoiceParameterValue toValue(String value, ProviderSnapshotMetadata metadata) {
        if (value == null && GitBranchInputCompatibility.enabled(this)) {
            return null;
        }
        return FoundryChoiceParameterValue.single(getName(), value, getDescription(), getEnvironmentExport(), metadata);
    }

    private FoundryChoiceParameterValue toMultiValue(List<String> values, ProviderSnapshotMetadata metadata) {
        return toMultiValue(values, values, metadata);
    }

    private FoundryChoiceParameterValue toMultiValue(
            List<String> values, List<String> submittedOrder, ProviderSnapshotMetadata metadata) {
        return FoundryChoiceParameterValue.multiple(
                getName(),
                values,
                submittedOrder,
                getDescription(),
                getEnvironmentExport(),
                getDelimiter(),
                getEnvironmentExportOrder(),
                metadata,
                "extended-choice".equals(getStandardApiInputMode()));
    }

    /** Keeps the submitted order for the values the canonical evaluation accepted. */
    private List<String> submittedOrder(List<String> canonical, List<String> submitted) {
        if ("extended-choice".equals(getStandardApiInputMode()) && submitted != null) {
            List<String> legacy = submitted.stream()
                    .filter(java.util.Objects::nonNull)
                    .filter(canonical::contains)
                    .toList();
            if (new HashSet<>(legacy).equals(new HashSet<>(canonical))) return legacy;
        }
        if (submitted == null || submitted.isEmpty() || submitted.size() == canonical.size()) {
            return submitted == null || submitted.isEmpty() ? canonical : submitted;
        }
        Set<String> remaining = new HashSet<>(canonical);
        List<String> ordered = new ArrayList<>();
        for (String value : submitted) {
            if (value != null && remaining.remove(value)) {
                ordered.add(value);
            }
        }
        return ordered.size() == canonical.size() ? List.copyOf(ordered) : canonical;
    }

    @Extension
    @Symbol("foundryChoice")
    public static final class DescriptorImpl extends ParameterDescriptor {
        @POST
        public void doPreviewImport(StaplerRequest2 request, StaplerResponse2 response) throws IOException {
            OptionImportEndpoint.previewForAdministrator(request, response);
        }

        @Override
        public FoundryChoiceParameterDefinition newInstance(StaplerRequest2 request, JSONObject formData)
                throws Descriptor.FormException {
            JSONObject data = JSONObject.fromObject(formData);
            String defaultValuesText = data.optString("defaultValuesText", "");
            String dependsOnText = data.optString("dependsOnText", "");
            data.remove("defaultValuesText");
            data.remove("dependsOnText");
            FoundryChoiceParameterDefinition definition =
                    (FoundryChoiceParameterDefinition) super.newInstance(request, data);
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
            return "Foundry Choice Parameter";
        }
    }
}
