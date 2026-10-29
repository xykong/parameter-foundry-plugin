package io.jenkins.plugins.parameterfoundry.pipeline;

import hudson.AbortException;
import hudson.Extension;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.Run;
import hudson.model.StringParameterValue;
import io.jenkins.plugins.parameterfoundry.definition.FoundryChoiceParameterValue;
import java.io.Serial;
import java.util.Set;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

/** Reads saved parameter text without reconstructing it from canonical selections or environment variables. */
public final class FoundryParameterTextStep extends Step {
    private final String name;
    private String fallbackIfMissing;

    @DataBoundConstructor
    public FoundryParameterTextStep(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Parameter name must not be blank.");
        }
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public String getFallbackIfMissing() {
        return fallbackIfMissing;
    }

    @DataBoundSetter
    public void setFallbackIfMissing(String fallbackIfMissing) {
        this.fallbackIfMissing = fallbackIfMissing;
    }

    @Override
    public StepExecution start(StepContext context) {
        return new Execution(context, name, fallbackIfMissing);
    }

    static String savedText(ParameterValue value) throws AbortException {
        if (value == null) {
            throw new AbortException("The current build has no accessible saved parameter with that name.");
        }
        if (value.isSensitive()) {
            throw new AbortException("Sensitive parameter text cannot be read by foundryParameterText.");
        }
        if (value instanceof FoundryChoiceParameterValue choice) {
            String literal = choice.getLegacyExportLiteral();
            if (literal != null) return literal;
            if (!choice.isMultiple()) return choice.getScalarValue();
            throw new AbortException(
                    "The saved multiple choice has no original text; canonical values cannot restore it.");
        }
        // Only these reviewed string types may invoke their value getter.
        if (value instanceof StringParameterValue string
                && (value.getClass() == StringParameterValue.class
                        || value.getClass()
                                .getName()
                                .equals(
                                        "com.cwctravel.hudson.plugins.extended_choice_parameter.ExtendedChoiceParameterValue"))) {
            return string.getValue();
        }
        throw new AbortException("This parameter type does not provide supported saved text.");
    }

    private static final class Execution extends SynchronousNonBlockingStepExecution<String> {
        @Serial
        private static final long serialVersionUID = 1L;

        private final String name;
        private final String fallbackIfMissing;

        Execution(StepContext context, String name, String fallbackIfMissing) {
            super(context);
            this.name = name;
            this.fallbackIfMissing = fallbackIfMissing;
        }

        @Override
        protected String run() throws Exception {
            Run<?, ?> run = getContext().get(Run.class);
            ParametersAction action = run.getAction(ParametersAction.class);
            ParameterValue value = action == null
                    ? null
                    : action.getParameters().stream()
                            .filter(parameter -> parameter.getName().equals(name))
                            .findFirst()
                            .orElse(null);
            if (value == null && fallbackIfMissing != null) return fallbackIfMissing;
            return savedText(value);
        }
    }

    @Extension
    public static final class DescriptorImpl extends StepDescriptor {
        @Override
        public String getFunctionName() {
            return "foundryParameterText";
        }

        @Override
        public String getDisplayName() {
            return "Read saved parameter text";
        }

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return Set.of(Run.class);
        }
    }
}
