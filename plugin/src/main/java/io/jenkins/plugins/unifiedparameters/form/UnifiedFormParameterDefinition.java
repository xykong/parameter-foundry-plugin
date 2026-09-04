package io.jenkins.plugins.unifiedparameters.form;

import hudson.AbortException;
import hudson.Extension;
import hudson.cli.CLICommand;
import hudson.model.Descriptor;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import io.jenkins.plugins.unifiedparameters.evaluation.EvaluationException;
import io.jenkins.plugins.unifiedparameters.renderer.TrustedRendererRegistry;
import java.io.IOException;
import java.io.Serial;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.StaplerRequest2;

public final class UnifiedFormParameterDefinition extends ParameterDefinition {

    @Serial
    private static final long serialVersionUID = 1L;

    private List<FormField> fields = List.of();
    private List<FormLayout> layouts = List.of();
    private String outputMode = "map";
    private String environmentExport = "json";
    private String renderer = "schema";
    private int modelVersion = 1;
    private transient String rebuildDefaultJson;

    @DataBoundConstructor
    public UnifiedFormParameterDefinition(String name) {
        super(name);
    }

    public List<FormField> getFields() {
        return fields == null ? List.of() : fields;
    }

    @DataBoundSetter
    public void setFields(List<FormField> fields) {
        this.fields = fields == null ? List.of() : List.copyOf(new ArrayList<>(fields));
    }

    public List<FormLayout> getLayouts() {
        return layouts == null ? List.of() : layouts;
    }

    @DataBoundSetter
    public void setLayouts(List<FormLayout> layouts) {
        this.layouts = layouts == null ? List.of() : List.copyOf(new ArrayList<>(layouts));
    }

    public String getOutputMode() {
        return outputMode == null || outputMode.isBlank() ? "map" : outputMode;
    }

    @DataBoundSetter
    public void setOutputMode(String outputMode) {
        this.outputMode = outputMode;
    }

    public String getEnvironmentExport() {
        return environmentExport == null || environmentExport.isBlank() ? "json" : environmentExport;
    }

    @DataBoundSetter
    public void setEnvironmentExport(String environmentExport) {
        this.environmentExport = environmentExport;
    }

    public String getRenderer() {
        return renderer == null || renderer.isBlank() ? "schema" : renderer;
    }

    @DataBoundSetter
    public void setRenderer(String renderer) {
        this.renderer = renderer;
    }

    public int getModelVersion() {
        return modelVersion;
    }

    public String getSchemaJson() {
        JSONObject schema = new JSONObject();
        net.sf.json.JSONArray encodedFields = new net.sf.json.JSONArray();
        for (FormField field : getFields()) {
            encodedFields.add(encodeField(field));
        }
        net.sf.json.JSONArray encodedLayouts = new net.sf.json.JSONArray();
        for (FormLayout layout : getLayouts()) {
            encodedLayouts.add(encodeLayout(layout));
        }
        schema.put("fields", encodedFields);
        schema.put("layouts", encodedLayouts);
        schema.put("renderer", getRenderer());
        return schema.toString();
    }

    public String getDefaultJson() {
        if (rebuildDefaultJson != null) {
            return rebuildDefaultJson;
        }
        try {
            return FormEvaluationService.toJson(FormEvaluationService.evaluate(this, null, false));
        } catch (EvaluationException exception) {
            return "{}";
        }
    }

    public List<String> getAvailableExternalRenderers() {
        return TrustedRendererRegistry.externalRendererIds(TrustedRendererRegistry.FAMILY_FORM);
    }

    public List<String> getRendererAdjuncts() {
        return TrustedRendererRegistry.trustedAdjuncts(getRenderer(), TrustedRendererRegistry.FAMILY_FORM);
    }

    @Override
    public ParameterValue createValue(StaplerRequest2 request) {
        String[] raw = request.getParameterValues(getName());
        boolean present = raw != null;
        if (present && raw.length != 1) {
            throw HttpResponses.errorWithoutStack(400, "Unified Form accepts exactly one JSON object.");
        }
        Object value = present ? raw[0] : null;
        return evaluate(value, present);
    }

    @Override
    public ParameterValue createValue(StaplerRequest2 request, JSONObject submitted) {
        boolean present = submitted.containsKey("value");
        Object value = present ? submitted.get("value") : null;
        if (value instanceof net.sf.json.JSONArray array) {
            if (array.size() != 1) {
                throw HttpResponses.errorWithoutStack(400, "Unified Form accepts exactly one JSON object.");
            }
            value = array.get(0);
        }
        return evaluate(value, present);
    }

    @Override
    public ParameterValue createValue(CLICommand command, String value) throws IOException, InterruptedException {
        try {
            return toValue(FormEvaluationService.evaluate(this, value, value != null));
        } catch (EvaluationException exception) {
            throw new AbortException(exception.getSafeMessage());
        }
    }

    public UnifiedFormParameterValue createTypedValue(Object value, boolean present) {
        return toValue(FormEvaluationService.evaluate(this, value, present));
    }

    @Override
    public UnifiedFormParameterValue getDefaultParameterValue() {
        try {
            return toValue(FormEvaluationService.evaluate(this, null, false));
        } catch (EvaluationException exception) {
            return null;
        }
    }

    @Override
    public boolean isValid(ParameterValue value) {
        if (!(value instanceof UnifiedFormParameterValue formValue)) {
            return false;
        }
        try {
            FormEvaluationService.evaluate(this, JSONObject.fromObject(formValue.getStructuredValue()), true);
            return true;
        } catch (EvaluationException exception) {
            return false;
        }
    }

    @Override
    public ParameterDefinition copyWithDefaultValue(ParameterValue defaultValue) {
        if (!(defaultValue instanceof UnifiedFormParameterValue formValue)) {
            return this;
        }
        UnifiedFormParameterDefinition copy = new UnifiedFormParameterDefinition(getName());
        copy.setDescription(getDescription());
        copy.setFields(getFields());
        copy.setLayouts(getLayouts());
        copy.setOutputMode(getOutputMode());
        copy.setEnvironmentExport(getEnvironmentExport());
        copy.setRenderer(getRenderer());
        copy.rebuildDefaultJson = formValue.getCanonicalJson();
        return copy;
    }

    private UnifiedFormParameterValue evaluate(Object value, boolean present) {
        try {
            return toValue(FormEvaluationService.evaluate(this, value, present));
        } catch (EvaluationException exception) {
            throw HttpResponses.errorWithoutStack(400, exception.getSafeMessage());
        }
    }

    private UnifiedFormParameterValue toValue(Map<String, Object> value) {
        Map<String, String> environmentVariables = new LinkedHashMap<>();
        for (FormField field : getFields()) {
            if (!field.isSecret() && field.getEnvironmentVariable() != null) {
                environmentVariables.put(field.getName(), field.getEnvironmentVariable());
            }
        }
        return new UnifiedFormParameterValue(
                getName(), value, getDescription(), getOutputMode(), getEnvironmentExport(), environmentVariables);
    }

    private static JSONObject encodeField(FormField field) {
        JSONObject encoded = new JSONObject();
        encoded.put("name", field.getName());
        encoded.put("type", field.getType());
        encoded.put("label", field.getLabel());
        encoded.put("description", field.getDescription());
        encoded.put("required", field.isRequired());
        encoded.put("secret", field.isSecret());
        encoded.put("defaultValue", field.getDefaultValue());
        encoded.put("minLength", field.getMinLength());
        encoded.put("maxLength", field.getMaxLength());
        encoded.put("pattern", field.getPattern());
        encoded.put("minimum", field.getMinimum());
        encoded.put("maximum", field.getMaximum());
        encoded.put("minItems", field.getMinItems());
        encoded.put("maxItems", field.getMaxItems());
        encoded.put("itemType", field.getItemType());
        encoded.put("environmentVariable", field.getEnvironmentVariable());
        net.sf.json.JSONArray options = new net.sf.json.JSONArray();
        for (io.jenkins.plugins.unifiedparameters.model.Option option : field.getChoices()) {
            JSONObject item = new JSONObject();
            item.put("value", option.getValue());
            item.put("label", option.getLabel());
            item.put("disabled", option.isDisabled());
            options.add(item);
        }
        encoded.put("choices", options);
        net.sf.json.JSONArray children = new net.sf.json.JSONArray();
        for (FormField child : field.getFields()) {
            children.add(encodeField(child));
        }
        encoded.put("fields", children);
        return encoded;
    }

    private static JSONObject encodeLayout(FormLayout layout) {
        JSONObject encoded = new JSONObject();
        encoded.put("id", layout.getId());
        encoded.put("type", layout.getType());
        encoded.put("title", layout.getTitle());
        encoded.put("columns", layout.getColumns());
        encoded.put("collapsed", layout.isCollapsed());
        encoded.put("fieldNames", net.sf.json.JSONArray.fromObject(layout.getFieldNames()));
        encoded.put("visibleWhenField", layout.getVisibleWhenField());
        encoded.put("visibleWhenValue", layout.getVisibleWhenValue());
        net.sf.json.JSONArray children = new net.sf.json.JSONArray();
        for (FormLayout child : layout.getChildren()) {
            children.add(encodeLayout(child));
        }
        encoded.put("children", children);
        return encoded;
    }

    @Extension
    @Symbol("unifiedForm")
    public static final class DescriptorImpl extends ParameterDescriptor {
        @Override
        public UnifiedFormParameterDefinition newInstance(StaplerRequest2 request, JSONObject formData)
                throws Descriptor.FormException {
            UnifiedFormParameterDefinition definition =
                    (UnifiedFormParameterDefinition) super.newInstance(request, formData);
            List<String> errors = FormEvaluationService.validateDefinition(definition);
            if (!errors.isEmpty()) {
                throw new Descriptor.FormException(String.join("; ", errors), "parameter");
            }
            return definition;
        }

        @Override
        public String getDisplayName() {
            return "Unified Form Parameter";
        }
    }
}
