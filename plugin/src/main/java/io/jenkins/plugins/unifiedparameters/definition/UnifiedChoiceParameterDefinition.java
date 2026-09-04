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
import io.jenkins.plugins.unifiedparameters.model.Option;
import io.jenkins.plugins.unifiedparameters.model.OptionImportEndpoint;
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

public final class UnifiedChoiceParameterDefinition extends AbstractUnifiedParameterDefinition {

    private static final long serialVersionUID = 1L;

    private String defaultValue;
    private boolean allowCustomValue;
    private String customValuePattern;
    private int customValueMaxLength = 256;

    @DataBoundConstructor
    public UnifiedChoiceParameterDefinition(String name) {
        super(name);
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    @DataBoundSetter
    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
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
            ResolvedProviderOptions resolved = optionsFor(request, ProviderOperation.BUILD, null);
            return toValue(
                    ParameterEvaluationService.evaluateSingle(
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
                    ParameterEvaluationService.evaluateSingle(
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
                    ParameterEvaluationService.evaluateSingle(
                            this, InputDecoders.fromCli(this, value), resolved.options()),
                    resolved.metadata());
        } catch (EvaluationException exception) {
            throw new AbortException(exception.getSafeMessage());
        }
    }

    @Override
    public UnifiedChoiceParameterValue getDefaultParameterValue() {
        try {
            ResolvedProviderOptions resolved = optionsForOwner(ProviderOperation.RESOLVE);
            return toValue(ParameterEvaluationService.evaluateSingle(this, DecodedInput.absent(), resolved.options()));
        } catch (EvaluationException exception) {
            return null;
        }
    }

    @Override
    public boolean isValid(ParameterValue value) {
        String submitted;
        if (value instanceof UnifiedChoiceParameterValue choiceValue) {
            submitted = choiceValue.getValue();
        } else if (value instanceof StringParameterValue stringValue) {
            submitted = stringValue.getValue();
        } else {
            return false;
        }
        try {
            ResolvedProviderOptions resolved = optionsForOwner(ProviderOperation.VALIDATE);
            ParameterEvaluationService.evaluateSingle(
                    this,
                    submitted == null
                            ? DecodedInput.present(java.util.List.of())
                            : DecodedInput.present(java.util.List.of(submitted)),
                    resolved.options());
            return true;
        } catch (EvaluationException exception) {
            return false;
        }
    }

    @Override
    public ParameterDefinition copyWithDefaultValue(ParameterValue defaultValue) {
        if (!(defaultValue instanceof UnifiedChoiceParameterValue choiceValue)) {
            return this;
        }
        UnifiedChoiceParameterDefinition copy = copyDefinition();
        copy.setDefaultMode("explicit");
        copy.setDefaultValue(choiceValue.getValue());
        return copy;
    }

    public boolean isDefault(String value) {
        UnifiedChoiceParameterValue defaultParameterValue = getDefaultParameterValue();
        return defaultParameterValue != null && java.util.Objects.equals(defaultParameterValue.getValue(), value);
    }

    @Override
    protected String getDefaultRenderer() {
        return "select";
    }

    @Override
    protected String getRendererFamily() {
        return io.jenkins.plugins.unifiedparameters.renderer.TrustedRendererRegistry.FAMILY_SINGLE_CHOICE;
    }

    @Override
    protected String getDefaultEnvironmentExport() {
        return "string";
    }

    @Override
    protected java.util.List<String> getFallbackDefaultValues() {
        UnifiedChoiceParameterValue value = getDefaultParameterValue();
        return value == null || value.getValue() == null ? java.util.List.of() : java.util.List.of(value.getValue());
    }

    @Override
    public boolean isMultiple() {
        return false;
    }

    private UnifiedChoiceParameterValue toValue(String value) {
        return new UnifiedChoiceParameterValue(getName(), value, getDescription(), getEnvironmentExport());
    }

    private UnifiedChoiceParameterDefinition copyDefinition() {
        UnifiedChoiceParameterDefinition copy = new UnifiedChoiceParameterDefinition(getName());
        copy.setDescription(getDescription());
        copy.setChoices(getChoices());
        copy.setProviderId(getProviderId());
        copy.setDependsOn(getDependsOn());
        copy.setRenderer(getRenderer());
        copy.setDefaultMode(getDefaultMode());
        copy.setDefaultValue(getDefaultValue());
        copy.setAllowCustomValue(isAllowCustomValue());
        copy.setCustomValuePattern(getCustomValuePattern());
        copy.setCustomValueMaxLength(getCustomValueMaxLength());
        copy.setRequired(isRequired());
        copy.setEnvironmentExport(getEnvironmentExport());
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

    private UnifiedChoiceParameterValue toValue(String value, ProviderSnapshotMetadata metadata) {
        return new UnifiedChoiceParameterValue(getName(), value, getDescription(), getEnvironmentExport(), metadata);
    }

    @Extension
    @Symbol("unifiedChoice")
    public static final class DescriptorImpl extends ParameterDescriptor {
        @POST
        public void doPreviewImport(StaplerRequest2 request, StaplerResponse2 response) throws IOException {
            OptionImportEndpoint.previewForAdministrator(request, response);
        }

        @Override
        public UnifiedChoiceParameterDefinition newInstance(StaplerRequest2 request, JSONObject formData)
                throws Descriptor.FormException {
            JSONObject data = JSONObject.fromObject(formData);
            String dependsOnText = data.optString("dependsOnText", "");
            data.remove("dependsOnText");
            UnifiedChoiceParameterDefinition definition =
                    (UnifiedChoiceParameterDefinition) super.newInstance(request, data);
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
            return "Unified Choice Parameter";
        }
    }
}
