package io.jenkins.plugins.unifiedparameters.migration.activechoices;

import hudson.Extension;
import hudson.model.ParameterDefinition;
import io.jenkins.plugins.unifiedparameters.migration.MigrationClassification;
import io.jenkins.plugins.unifiedparameters.migration.MigrationPlan;
import io.jenkins.plugins.unifiedparameters.migration.SourceMigrationAdapter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

@Extension
public final class ActiveChoicesMigrationAdapter extends SourceMigrationAdapter {

    private static final String CHOICE = "org.biouno.unochoice.ChoiceParameter";
    private static final String CASCADE = "org.biouno.unochoice.CascadeChoiceParameter";
    private static final String REFERENCE = "org.biouno.unochoice.DynamicReferenceParameter";

    @Override
    public String getSourcePluginId() {
        return "uno-choice";
    }

    @Override
    public boolean supports(ParameterDefinition definition) {
        return definition != null
                && List.of(CHOICE, CASCADE, REFERENCE)
                        .contains(definition.getClass().getName());
    }

    @Override
    public MigrationPlan inspect(ParameterDefinition definition) {
        if (!supports(definition)) {
            throw new IllegalArgumentException("Unsupported source definition");
        }
        String className = definition.getClass().getName();
        List<String> dependencies = CASCADE.equals(className)
                ? parseDependencies(readString(definition, "getReferencedParameters"))
                : List.of();
        boolean form = REFERENCE.equals(className);
        String skeleton = form ? formSkeleton(definition) : choiceSkeleton(definition, dependencies);
        List<String> decisions = form
                ? List.of(
                        "Replace formatted HTML with typed Unified Form fields or a reviewed Renderer companion.",
                        "Choose which source values are business outputs rather than presentation-only content.")
                : List.of(
                        "Choose a reviewed Script, REST, Managed Config, or domain Provider without executing the source script during migration.",
                        "Confirm default, empty, fallback, and error behavior for the normalized option schema.");
        return new MigrationPlan(
                MigrationClassification.C,
                "Active Choices script intent requires a governed Provider/Form refactor; source code was not executed.",
                null,
                decisions,
                skeleton,
                verificationFixture(definition, dependencies, form));
    }

    private static String choiceSkeleton(ParameterDefinition definition, List<String> dependencies) {
        String dependencyLine = dependencies.isEmpty()
                ? ""
                : "    dependsOn: ["
                        + dependencies.stream()
                                .map(ActiveChoicesMigrationAdapter::quote)
                                .reduce((left, right) -> left + ", " + right)
                                .orElse("")
                        + "],\n";
        return "unifiedChoice(\n"
                + "    name: "
                + quote(definition.getName())
                + ",\n"
                + "    providerId: 'TODO-governed-provider',\n"
                + dependencyLine
                + "    renderer: 'searchable'\n"
                + ")";
    }

    private static String formSkeleton(ParameterDefinition definition) {
        return "unifiedForm(\n"
                + "    name: "
                + quote(definition.getName())
                + ",\n"
                + "    fields: [/* TODO typed fields replacing formatted HTML */],\n"
                + "    renderer: 'schema'\n"
                + ")";
    }

    private static String verificationFixture(ParameterDefinition definition, List<String> dependencies, boolean form) {
        return "For "
                + definition.getName()
                + ", compare each canonical dependency tuple "
                + dependencies
                + " across browser resolve, Typed API, standard build, CLI, fallback/error, and Rebuild; "
                + (form
                        ? "verify CSP-safe typed rendering and structured output."
                        : "compare normalized option values and defaults.");
    }

    private static String readString(Object source, String methodName) {
        try {
            Method method = source.getClass().getMethod(methodName);
            Object value = method.invoke(source);
            return value == null ? "" : String.valueOf(value);
        } catch (NoSuchMethodException exception) {
            return "";
        } catch (IllegalAccessException | InvocationTargetException exception) {
            return "";
        }
    }

    private static List<String> parseDependencies(String value) {
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .distinct()
                .toList();
    }

    private static String quote(String value) {
        return "'" + value.replace("\\", "\\\\").replace("'", "\\'") + "'";
    }
}
