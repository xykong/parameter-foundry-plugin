package io.jenkins.plugins.parameterfoundry.definition;

import hudson.AbortException;
import hudson.Extension;
import hudson.cli.CLICommand;
import hudson.model.Descriptor;
import hudson.model.Job;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.model.StringParameterValue;
import io.jenkins.plugins.parameterfoundry.evaluation.ActiveChoicesSingleCompatibility;
import io.jenkins.plugins.parameterfoundry.evaluation.DecodedInput;
import io.jenkins.plugins.parameterfoundry.evaluation.DependencyValues;
import io.jenkins.plugins.parameterfoundry.evaluation.EvaluationException;
import io.jenkins.plugins.parameterfoundry.evaluation.ExtendedChoiceDefaultCompatibility;
import io.jenkins.plugins.parameterfoundry.evaluation.ExtendedChoiceFormCompatibility;
import io.jenkins.plugins.parameterfoundry.evaluation.ExtendedChoiceSingleCompatibility;
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
    private String activeChoicesApiDefaultMode = "first";
    private String activeChoicesApiDefaultLiteral = "";
    private Integer activeChoicesFilterLength;
    private transient boolean activeChoicesRebuildInput;

    public String getActiveChoicesApiDefaultMode() {
        return activeChoicesApiDefaultMode == null ? "first" : activeChoicesApiDefaultMode;
    }

    @DataBoundSetter
    public void setActiveChoicesApiDefaultMode(String mode) {
        activeChoicesApiDefaultMode = mode;
    }

    public String getActiveChoicesApiDefaultLiteral() {
        return activeChoicesApiDefaultLiteral == null ? "" : activeChoicesApiDefaultLiteral;
    }

    @DataBoundSetter
    public void setActiveChoicesApiDefaultLiteral(String literal) {
        activeChoicesApiDefaultLiteral = literal;
    }

    public boolean isActiveChoicesSingleInput() {
        return ActiveChoicesSingleCompatibility.enabled(this);
    }

    public int getActiveChoicesFilterLength() {
        return activeChoicesFilterLength == null ? 1 : activeChoicesFilterLength;
    }

    @DataBoundSetter
    public void setActiveChoicesFilterLength(int length) {
        activeChoicesFilterLength = length;
    }

    private String legacyDefaultLiteral;
    private boolean legacyEmptyChoiceAllowed;
    private boolean legacyEmptyOptionsOmitCli;

    public boolean isLegacyEmptyOptionsOmitCli() {
        return legacyEmptyOptionsOmitCli;
    }

    @DataBoundSetter
    public void setLegacyEmptyOptionsOmitCli(boolean omit) {
        legacyEmptyOptionsOmitCli = omit;
    }

    public boolean isLegacyEmptyChoiceAllowed() {
        return legacyEmptyChoiceAllowed;
    }

    @DataBoundSetter
    public void setLegacyEmptyChoiceAllowed(boolean allowed) {
        this.legacyEmptyChoiceAllowed = allowed;
    }

    private String gitSelectionMode = "default";
    private transient boolean gitRebuildInput;
    private transient boolean extendedChoiceRebuildInput;
    private transient String extendedChoiceRebuildLiteral;
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

    public boolean isExtendedChoiceSingleInput() {
        return ExtendedChoiceSingleCompatibility.enabled(this);
    }

    public boolean isLegacyRebuildInput() {
        return activeChoicesRebuildInput && isActiveChoicesSingleInput()
                || isGitRebuildInput()
                || extendedChoiceRebuildInput
                        && (ExtendedChoiceDefaultCompatibility.enabled(this) || isExtendedChoiceSingleInput());
    }

    public String getLegacyRebuildValue() {
        return isGitRebuildInput() ? getDefaultValue() : extendedChoiceRebuildLiteral;
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
        if (isExtendedChoiceSingleInput() || isActiveChoicesSingleInput()) return getSelectableOptions();
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
    public boolean isFallbackTruncated() {
        return !isExtendedChoiceSingleInput() && !isActiveChoicesSingleInput() && super.isFallbackTruncated();
    }

    @Override
    public ParameterValue createValue(StaplerRequest2 request) {
        try {
            if (isActiveChoicesSingleInput()) {
                ActiveChoicesSingleCompatibility.requireCompatible(this);
                String raw = ActiveChoicesSingleCompatibility.standard(request.getParameterValues(getName()));
                validateActiveChoicesSnapshot(request, null);
                if (raw == null) return getDefaultParameterValue();
                return ActiveChoicesSingleCompatibility.value(this, raw, null);
            }
            if (isExtendedChoiceSingleInput()) {
                return ExtendedChoiceSingleCompatibility.fields(this, request.getParameterValues(getName()));
            }
            DecodedInput input = InputDecoders.fromStandardRequest(this, request);
            if (!input.present() && ExtendedChoiceDefaultCompatibility.enabled(this)) {
                return ExtendedChoiceDefaultCompatibility.evaluate(this);
            }
            ResolvedProviderOptions resolved = optionsFor(request, ProviderOperation.BUILD, null, input);
            if (isMultiple()) {
                List<String> canonical = ExtendedChoiceDefaultCompatibility.evaluateSubmission(
                        this, input, resolved.options(), resolved.matrix());
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
            if (isActiveChoicesSingleInput()) {
                ActiveChoicesSingleCompatibility.requireCompatible(this);
                validateActiveChoicesSnapshot(request, submitted);
                return ActiveChoicesSingleCompatibility.value(
                        this, ActiveChoicesSingleCompatibility.form(submitted), null);
            }
            if (isExtendedChoiceSingleInput()) {
                return ExtendedChoiceSingleCompatibility.form(this, submitted);
            }
            if (ExtendedChoiceDefaultCompatibility.enabled(this)) {
                return ExtendedChoiceFormCompatibility.evaluate(this, submitted);
            }
            DecodedInput input = InputDecoders.fromSubmittedJson(this, submitted);
            ResolvedProviderOptions resolved = optionsFor(request, ProviderOperation.BUILD, submitted, input);
            if (isMultiple()) {
                List<String> canonical = ExtendedChoiceDefaultCompatibility.evaluateSubmission(
                        this, input, resolved.options(), resolved.matrix());
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
            if (isActiveChoicesSingleInput())
                return ActiveChoicesSingleCompatibility.value(this, ActiveChoicesSingleCompatibility.cli(value), null);
            if (isExtendedChoiceSingleInput()) {
                return ExtendedChoiceSingleCompatibility.cli(this, value);
            }
            DecodedInput input = InputDecoders.fromCli(this, value);
            if (!input.present() && ExtendedChoiceDefaultCompatibility.enabled(this)) {
                return ExtendedChoiceDefaultCompatibility.evaluate(this);
            }
            if (ExtendedChoiceDefaultCompatibility.enabled(this)
                    && getChoices().isEmpty()
                    && isLegacyEmptyOptionsOmitCli()) {
                ExtendedChoiceDefaultCompatibility.evaluateSubmission(this, input, getSelectableOptions(), getTable());
                return null;
            }
            ResolvedProviderOptions resolved = !needsOptions(input)
                    ? new ResolvedProviderOptions(List.of(), null, null, null, false)
                    : isProviderBacked()
                            ? ProviderSubmission.resolveForCli(
                                    ProviderSubmission.requireOwningJob(this), this, ProviderOperation.BUILD, command)
                            : new ResolvedProviderOptions(getSelectableOptions(), getTable(), null, null, false);
            if (isMultiple()) {
                List<String> canonical = ExtendedChoiceDefaultCompatibility.evaluateSubmission(
                        this, input, resolved.options(), resolved.matrix());
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
            if (isActiveChoicesSingleInput()) {
                ActiveChoicesSingleCompatibility.requireCompatible(this);
                ResolvedProviderOptions resolved = "literal".equals(getActiveChoicesApiDefaultMode())
                        ? new ResolvedProviderOptions(List.of(), null, null, null, false)
                        : ProviderSubmission.resolveForDefault(this);
                return ActiveChoicesSingleCompatibility.defaultValue(this, resolved.options(), resolved.metadata());
            }
            if (isExtendedChoiceSingleInput()) {
                return ExtendedChoiceSingleCompatibility.defaultValue(this);
            }
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
                    && choice.getLegacyExportLiteral() != null
                    && !ExtendedChoiceDefaultCompatibility.enabled(this)) {
                return false;
            }
            if (GitBranchInputCompatibility.enabled(this)
                    || isExtendedChoiceSingleInput()
                    || isActiveChoicesSingleInput()) {
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
        if (isActiveChoicesSingleInput()
                && defaultValue instanceof FoundryChoiceParameterValue multiple
                && multiple.isMultiple()) return this;
        if (isActiveChoicesSingleInput()
                && (defaultValue instanceof StringParameterValue
                        || defaultValue instanceof FoundryChoiceParameterValue)) {
            String literal = defaultValue instanceof StringParameterValue string
                    ? string.getValue()
                    : ((FoundryChoiceParameterValue) defaultValue).getScalarValue();
            FoundryChoiceParameterDefinition copy = copyDefinition();
            copy.activeChoicesRebuildInput = true;
            copy.extendedChoiceRebuildLiteral = literal == null ? "" : literal;
            copy.setActiveChoicesApiDefaultMode("literal");
            copy.setActiveChoicesApiDefaultLiteral(literal);
            return copy;
        }
        if (!(defaultValue instanceof FoundryChoiceParameterValue choiceValue)) {
            return this;
        }
        FoundryChoiceParameterDefinition copy = copyDefinition();
        copy.gitRebuildInput = GitBranchInputCompatibility.enabled(this);
        copy.extendedChoiceRebuildInput =
                (ExtendedChoiceDefaultCompatibility.enabled(this) && choiceValue.getLegacyExportLiteral() != null)
                        || isExtendedChoiceSingleInput();
        copy.extendedChoiceRebuildLiteral =
                isMultiple() ? choiceValue.getLegacyExportLiteral() : choiceValue.getScalarValue();
        copy.setDefaultMode("explicit");
        if (isMultiple()) {
            copy.setDefaultValues(choiceValue.getValues());
            copy.setLegacyDefaultLiteral(choiceValue.getLegacyExportLiteral());
        } else if (isExtendedChoiceSingleInput()) {
            String historical = choiceValue.getScalarValue();
            copy.setLegacyDefaultLiteral(historical);
            boolean offered = historical != null && findOption(historical) != null;
            copy.setDefaultMode(offered ? "explicit" : "none");
            copy.setDefaultValue(offered ? historical : null);
        } else {
            copy.setDefaultValue(choiceValue.getScalarValue());
        }
        return copy;
    }

    public String getUiDefaultValue() {
        if (isMultiple()) return null;
        if (isActiveChoicesSingleInput())
            return isProviderBacked()
                    ? null
                    : ParameterEvaluationService.evaluateSingle(this, DecodedInput.absent(), getSelectableOptions());
        ParameterValue value = getDefaultParameterValue();
        return value == null ? null : (String) value.getValue();
    }

    public boolean isDefault(String value) {
        if (isExtendedChoiceSingleInput() || isActiveChoicesSingleInput() && !isProviderBacked())
            return java.util.Objects.equals(
                    value,
                    ParameterEvaluationService.evaluateSingle(this, DecodedInput.absent(), getSelectableOptions()));
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
        if (isActiveChoicesSingleInput()) {
            String value = getUiDefaultValue();
            return value == null ? List.of() : List.of(value);
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
        copy.setActiveChoicesApiDefaultMode(getActiveChoicesApiDefaultMode());
        copy.setActiveChoicesApiDefaultLiteral(getActiveChoicesApiDefaultLiteral());
        copy.setActiveChoicesFilterLength(getActiveChoicesFilterLength());
        copy.setLegacyDefaultLiteral(getLegacyDefaultLiteral());
        copy.setLegacyEmptyChoiceAllowed(isLegacyEmptyChoiceAllowed());
        copy.setLegacyEmptyOptionsOmitCli(isLegacyEmptyOptionsOmitCli());
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

    private void validateActiveChoicesSnapshot(StaplerRequest2 request, JSONObject submitted) {
        String snapshot = submitted == null ? null : submitted.optString("snapshotId", null);
        if ((snapshot == null || snapshot.isBlank()) && request != null)
            snapshot = request.getParameter(ProviderSubmission.snapshotRequestField(this));
        if (snapshot == null || snapshot.isBlank()) return;
        if (!isProviderBacked())
            throw new EvaluationException(
                    io.jenkins.plugins.parameterfoundry.evaluation.ErrorCode.SNAPSHOT_INVALID,
                    getName(),
                    "Static choices do not accept Provider snapshots.");
        optionsFor(request, ProviderOperation.BUILD, submitted, DecodedInput.present(List.of("")));
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
                            DependencyValues.fromJson(
                                    job,
                                    this,
                                    DependencyValues.parseJsonObject(
                                            this,
                                            submitted == null ? null : submitted.optString("dependenciesJson", null))),
                            submitted == null ? null : submitted.optString("snapshotId", null),
                            operation,
                            needsOptions(input));
        }
        return ProviderSubmission.resolve(job, this, operation, request, submitted, needsOptions(input));
    }

    public boolean needsOptions(DecodedInput input) {
        if (isActiveChoicesSingleInput()) return !input.present() && "first".equals(getActiveChoicesApiDefaultMode());
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

    /** Constructs a value after shared evaluation, retaining the caller's ordered submission. */
    public FoundryChoiceParameterValue createEvaluatedMultipleValue(
            List<String> canonical, List<String> submitted, ProviderSnapshotMetadata metadata) {
        return toMultiValue(canonical, submittedOrder(canonical, submitted), metadata);
    }

    private FoundryChoiceParameterValue toMultiValue(
            List<String> values, List<String> submittedOrder, ProviderSnapshotMetadata metadata) {
        if (ExtendedChoiceDefaultCompatibility.enabled(this)) {
            return FoundryChoiceParameterValue.legacySubmission(
                    getName(),
                    values,
                    submittedOrder,
                    getDescription(),
                    getEnvironmentExport(),
                    getDelimiter(),
                    String.join(getDelimiter(), submittedOrder));
        }
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
                    .filter(value -> canonical.contains(value) || isLegacyEmptyChoiceAllowed() && value.isEmpty())
                    .toList();
            if (new HashSet<>(legacy.stream().filter(value -> !value.isEmpty()).toList())
                    .equals(new HashSet<>(canonical))) return legacy;
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
