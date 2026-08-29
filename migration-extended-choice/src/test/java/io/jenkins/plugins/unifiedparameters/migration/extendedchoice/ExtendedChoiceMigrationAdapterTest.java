package io.jenkins.plugins.unifiedparameters.migration.extendedchoice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cwctravel.hudson.plugins.extended_choice_parameter.ExtendedChoiceParameterDefinition;
import io.jenkins.plugins.unifiedparameters.definition.UnifiedChoiceParameterDefinition;
import io.jenkins.plugins.unifiedparameters.definition.UnifiedMultiChoiceParameterDefinition;
import io.jenkins.plugins.unifiedparameters.migration.MigrationClassification;
import io.jenkins.plugins.unifiedparameters.migration.MigrationPlan;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExtendedChoiceMigrationAdapterTest {

    private final ExtendedChoiceMigrationAdapter adapter = new ExtendedChoiceMigrationAdapter();

    @Test
    void staticSingleChoiceHasDirectMapping() {
        ExtendedChoiceParameterDefinition source =
                source(ExtendedChoiceParameterDefinition.PARAMETER_TYPE_RADIO, "staging,production", "staging", null);

        MigrationPlan plan = adapter.inspect(source);

        assertEquals(MigrationClassification.A, plan.getClassification());
        UnifiedChoiceParameterDefinition target =
                assertInstanceOf(UnifiedChoiceParameterDefinition.class, plan.getTarget());
        assertEquals("radio", target.getRenderer());
        assertEquals("staging", target.getDefaultValue());
        assertEquals(
                List.of("staging", "production"),
                target.getChoices().stream().map(option -> option.getValue()).toList());
    }

    @Test
    void staticMultipleChoiceRequiresOrderDecision() {
        ExtendedChoiceParameterDefinition source = source(
                ExtendedChoiceParameterDefinition.PARAMETER_TYPE_CHECK_BOX, "web,android,ios", "web,android", null);

        MigrationPlan plan = adapter.inspect(source);

        assertEquals(MigrationClassification.B, plan.getClassification());
        UnifiedMultiChoiceParameterDefinition target =
                assertInstanceOf(UnifiedMultiChoiceParameterDefinition.class, plan.getTarget());
        assertEquals("checkbox", target.getRenderer());
        assertEquals("delimited", target.getStandardApiInputMode());
        assertEquals(List.of("web", "android"), target.getDefaultValues());
        assertEquals(1, plan.getDecisions().size());
    }

    @Test
    void groovySourceIsNotExecutedOrAutomaticallyConverted() {
        ExtendedChoiceParameterDefinition source =
                source(ExtendedChoiceParameterDefinition.PARAMETER_TYPE_SINGLE_SELECT, null, null, "return ['unsafe']");

        MigrationPlan plan = adapter.inspect(source);

        assertEquals(MigrationClassification.C, plan.getClassification());
        assertNull(plan.getTarget());
        assertTrue(plan.getTargetSkeleton().contains("providerId: 'TODO-governed-provider'"));
        assertTrue(plan.getVerificationFixture().contains("Typed API build"));
    }

    @Test
    void legacyNullEmptyOptionAndStaleDefaultBecomeExplicitDecisions() {
        ExtendedChoiceParameterDefinition source = source(
                ExtendedChoiceParameterDefinition.PARAMETER_TYPE_MULTI_SELECT,
                "android,,ios",
                "Choose platforms",
                null);

        MigrationPlan plan = adapter.inspect(source);

        assertEquals(MigrationClassification.B, plan.getClassification());
        UnifiedMultiChoiceParameterDefinition target =
                assertInstanceOf(UnifiedMultiChoiceParameterDefinition.class, plan.getTarget());
        assertEquals("none", target.getDefaultMode());
        assertEquals(
                List.of("android", "ios"),
                target.getChoices().stream().map(option -> option.getValue()).toList());
        assertTrue(plan.getDecisions().stream().anyMatch(decision -> decision.contains("empty source option")));
        assertTrue(plan.getDecisions().stream().anyMatch(decision -> decision.contains("non-selectable default")));
    }

    private static ExtendedChoiceParameterDefinition source(
            String type, String values, String defaults, String groovyScript) {
        return new ExtendedChoiceParameterDefinition(
                "TARGET",
                type,
                values,
                null,
                null,
                groovyScript,
                null,
                null,
                null,
                null,
                defaults,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                false,
                5,
                "Synthetic migration fixture",
                ",");
    }
}
