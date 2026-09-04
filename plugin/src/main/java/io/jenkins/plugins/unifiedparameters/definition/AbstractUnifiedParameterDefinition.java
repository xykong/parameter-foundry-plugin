package io.jenkins.plugins.unifiedparameters.definition;

import hudson.model.Job;
import hudson.model.ParameterDefinition;
import hudson.model.ParametersDefinitionProperty;
import io.jenkins.plugins.unifiedparameters.model.Option;
import io.jenkins.plugins.unifiedparameters.renderer.TrustedRendererRegistry;
import java.io.Serial;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private int modelVersion = 1;

    protected AbstractUnifiedParameterDefinition(String name) {
        super(name);
    }

    public List<Option> getChoices() {
        return choices == null ? List.of() : choices;
    }

    @DataBoundSetter
    public void setChoices(List<Option> choices) {
        this.choices = choices == null ? List.of() : List.copyOf(new ArrayList<>(choices));
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

    public int getModelVersion() {
        return modelVersion;
    }

    public Option findOption(String value) {
        return findOption(getChoices(), value);
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
        return firstEnabledOption(getChoices());
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
        List<Option> currentChoices = getChoices();
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
        return getChoices().size();
    }

    public List<String> getAvailableExternalRenderers() {
        return TrustedRendererRegistry.externalRendererIds(getRendererFamily());
    }

    public List<String> getRendererAdjuncts() {
        return TrustedRendererRegistry.trustedAdjuncts(getRenderer(), getRendererFamily());
    }

    public boolean isFallbackTruncated() {
        return getChoices().size() > NO_JAVASCRIPT_OPTION_LIMIT;
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
