package io.jenkins.plugins.parameterfoundry.companion.script;

import groovy.lang.Binding;
import hudson.Extension;
import io.jenkins.plugins.parameterfoundry.evaluation.ErrorCode;
import io.jenkins.plugins.parameterfoundry.model.Option;
import io.jenkins.plugins.parameterfoundry.provider.ProviderException;
import io.jenkins.plugins.parameterfoundry.provider.ProviderLoadContext;
import io.jenkins.plugins.parameterfoundry.provider.extension.ScriptProviderExtension;
import io.jenkins.plugins.parameterfoundry.provider.extension.ScriptProviderSecurityContract;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.scriptsecurity.sandbox.groovy.SecureGroovyScript;
import org.jenkinsci.plugins.scriptsecurity.scripts.ApprovalContext;

@Extension
public final class SandboxedScriptProviderExtension extends ScriptProviderExtension {

    public static final String ENGINE_ID = "sandbox-groovy";
    private static final int MAX_OPTIONS = 10_000;
    private static final int MAX_VALUE_LENGTH = 1_024;
    private static final int MAX_TEXT_LENGTH = 4_096;
    private static final int MAX_TAG_LENGTH = 256;
    private static final int MAX_TAGS = 32;
    private static final Logger LOGGER = Logger.getLogger(SandboxedScriptProviderExtension.class.getName());

    @Override
    public String getEngineId() {
        return ENGINE_ID;
    }

    @Override
    public ScriptProviderSecurityContract getSecurityContract() {
        return new ScriptProviderSecurityContract(1, false, true, true, true, true, true, Set.of("catalog"));
    }

    @Override
    public JSONObject getOutputSchema() {
        JSONObject item = new JSONObject();
        item.put("type", "object");
        item.put("required", List.of("value"));
        JSONObject schema = new JSONObject();
        schema.put("type", "array");
        schema.put("items", item);
        return schema;
    }

    @Override
    public List<Option> evaluate(ProviderLoadContext context, String governedSourceReference) {
        ScriptCatalogEntry entry = ScriptProviderCatalog.get()
                .findEntry(governedSourceReference)
                .orElseThrow(() -> unavailable(
                        context, "Governed script reference is not present in the Script Provider catalog."));
        context.checkCancelled();
        try {
            SecureGroovyScript configured =
                    entry.getScript().configuring(approvalContext(context, governedSourceReference));
            Binding binding = new Binding();
            binding.setVariable("dependencies", context.request().dependencyValues());
            binding.setVariable("operation", context.request().operation().name());
            Object raw = configured.evaluate(getClass().getClassLoader(), binding);
            context.checkCancelled();
            List<Option> options = normalize(raw, context.request().providerId());
            LOGGER.log(
                    Level.INFO,
                    "Sandboxed Script Provider evaluated catalog entry {0} for provider {1}, operation {2}, principal {3}, options {4}.",
                    new Object[] {
                        entry.getId(),
                        context.request().providerId(),
                        context.request().operation(),
                        context.request().principal(),
                        options.size()
                    });
            return options;
        } catch (ProviderException exception) {
            throw exception;
        } catch (Exception exception) {
            throw unavailable(context, "Sandboxed Script Provider evaluation failed.", exception);
        }
    }

    private static ApprovalContext approvalContext(ProviderLoadContext context, String sourceReference) {
        String principal = context.request().principal();
        ApprovalContext approval =
                ApprovalContext.create().withItemAsKey(context.request().job()).withKey(sourceReference);
        return principal == null || principal.isBlank() ? approval : approval.withUser(principal);
    }

    private static List<Option> normalize(Object raw, String providerId) {
        if (!(raw instanceof List<?> values)) {
            throw new ProviderException(
                    ErrorCode.PROVIDER_UNAVAILABLE, providerId, "Sandboxed Script Provider must return a list.");
        }
        if (values.size() > MAX_OPTIONS) {
            throw new ProviderException(
                    ErrorCode.PROVIDER_UNAVAILABLE,
                    providerId,
                    "Sandboxed Script Provider returned more than 10,000 options.");
        }
        List<Option> result = new ArrayList<>(values.size());
        for (Object value : values) {
            result.add(normalizeOption(value, providerId));
        }
        return List.copyOf(result);
    }

    private static Option normalizeOption(Object raw, String providerId) {
        if (raw instanceof Option option) {
            return copyOption(option, providerId);
        }
        if (raw instanceof String value) {
            validateValue(value, providerId);
            return new Option(value);
        }
        if (!(raw instanceof Map<?, ?> map)) {
            throw new ProviderException(
                    ErrorCode.PROVIDER_UNAVAILABLE,
                    providerId,
                    "Sandboxed Script Provider returned an unsupported option shape.");
        }
        String value = stringValue(map, "value");
        validateValue(value, providerId);
        Option option = new Option(value);
        option.setLabel(boundedText(stringValue(map, "label"), "label", providerId));
        option.setDescription(boundedText(stringValue(map, "description"), "description", providerId));
        option.setGroup(boundedText(stringValue(map, "group"), "group", providerId));
        option.setDisabled(booleanValue(map, "disabled"));
        option.setDisabledReason(boundedText(stringValue(map, "disabledReason"), "disabledReason", providerId));
        Object tags = map.get("tags");
        if (tags instanceof List<?> tagList) {
            if (tagList.size() > MAX_TAGS) {
                throw invalidOutput(providerId, "Sandboxed Script Provider returned too many tags.");
            }
            option.setTags(tagList.stream()
                    .map(String::valueOf)
                    .map(tag -> boundedText(tag, "tag", providerId))
                    .toList());
        }
        return option;
    }

    private static Option copyOption(Option source, String providerId) {
        validateValue(source.getValue(), providerId);
        Option copy = new Option(source.getValue());
        copy.setLabel(boundedText(source.getLabel(), "label", providerId));
        copy.setDescription(boundedText(source.getDescription(), "description", providerId));
        copy.setGroup(boundedText(source.getGroup(), "group", providerId));
        copy.setDisabled(source.isDisabled());
        copy.setDisabledReason(boundedText(source.getDisabledReason(), "disabledReason", providerId));
        if (source.getTags().size() > MAX_TAGS) {
            throw invalidOutput(providerId, "Sandboxed Script Provider returned too many tags.");
        }
        copy.setTags(source.getTags().stream()
                .map(tag -> boundedText(tag, "tag", providerId))
                .toList());
        return copy;
    }

    private static String stringValue(Map<?, ?> map, String key) {
        Object value = map.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private static boolean booleanValue(Map<?, ?> map, String key) {
        Object value = map.get(key);
        return value instanceof Boolean bool ? bool : Boolean.parseBoolean(String.valueOf(value));
    }

    private static void validateValue(String value, String providerId) {
        if (value == null || value.isBlank() || value.length() > MAX_VALUE_LENGTH) {
            throw invalidOutput(providerId, "Sandboxed Script Provider returned an invalid option value.");
        }
    }

    private static String boundedText(String value, String field, String providerId) {
        if (value != null && value.length() > MAX_TEXT_LENGTH) {
            throw invalidOutput(providerId, "Sandboxed Script Provider returned an oversized " + field + ".");
        }
        if ("tag".equals(field) && value != null && value.length() > MAX_TAG_LENGTH) {
            throw invalidOutput(providerId, "Sandboxed Script Provider returned an oversized tag.");
        }
        return value;
    }

    private static ProviderException invalidOutput(String providerId, String message) {
        return new ProviderException(ErrorCode.PROVIDER_UNAVAILABLE, providerId, message);
    }

    private static ProviderException unavailable(ProviderLoadContext context, String message) {
        return unavailable(context, message, null);
    }

    private static ProviderException unavailable(ProviderLoadContext context, String message, Throwable cause) {
        ProviderException exception = new ProviderException(
                ErrorCode.PROVIDER_UNAVAILABLE, context.request().providerId(), message);
        if (cause != null) {
            exception.initCause(cause);
        }
        return exception;
    }
}
