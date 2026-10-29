package io.jenkins.plugins.parameterfoundry.migration.extendedchoice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cwctravel.hudson.plugins.extended_choice_parameter.ExtendedChoiceParameterDefinition;
import io.jenkins.plugins.parameterfoundry.definition.FoundryChoiceParameterDefinition;
import io.jenkins.plugins.parameterfoundry.migration.MigrationClassification;
import io.jenkins.plugins.parameterfoundry.migration.MigrationPlan;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExtendedChoiceMigrationAdapterTest {

    private final ExtendedChoiceMigrationAdapter adapter = new ExtendedChoiceMigrationAdapter();

    @Test
    void literalDescriptionsMatchNativeLabelsAcrossStaticControls() {
        for (String type : List.of("PT_SINGLE_SELECT", "PT_MULTI_SELECT", "PT_RADIO", "PT_CHECKBOX")) {
            for (String[] fixture : List.of(
                    new String[] {"APP,MINI", " app-label,mini-label ", "app-label", "mini-label"},
                    new String[] {"a,,b", "one,,two,three", "one", "three"},
                    new String[] {"a,a,b", "first,last,third", "last", "last", "third"},
                    new String[] {"a,b,c", "one, ,", "one", "b", "c"},
                    new String[] {"a,b,", "<literal>,second,extra", "<literal>", "second"},
                    new String[] {"a,b", " \t\u2003", "a", "b"})) {
                var source = source(type, fixture[0], "a", null);
                source.setDescriptionPropertyValue(fixture[1]);
                var target = assertInstanceOf(
                        FoundryChoiceParameterDefinition.class,
                        adapter.inspect(source).getTarget());
                assertEquals(
                        List.of(fixture).subList(2, fixture.length),
                        target.getChoices().stream()
                                .map(option -> option.getLabel())
                                .toList());
            }
        }
    }

    @Test
    void dynamicDescriptionSourcesAreGovernedWithoutEvaluation() {
        var source = source("PT_RADIO", "APP,MINI", "APP", null);
        source.setDescriptionGroovyScript("throw new AssertionError('Must not execute')");
        assertEquals(MigrationClassification.C, adapter.inspect(source).getClassification());
        assertNull(adapter.inspect(source).getTarget());
        source.setDescriptionGroovyScript(null);
        source.setDescriptionPropertyFile("/must-not-read/descriptions.properties");
        assertEquals(MigrationClassification.C, adapter.inspect(source).getClassification());
        assertNull(adapter.inspect(source).getTarget());
    }

    @Test
    void multipleListPreservesConfiguredRowsEvenWithoutOptions() {
        for (String raw : new String[] {"he,mi", "he,he,mi", ""}) {
            var source = source(ExtendedChoiceParameterDefinition.PARAMETER_TYPE_MULTI_SELECT, raw, "he", null);
            source.setVisibleItemCount(16);
            var target = assertInstanceOf(
                    FoundryChoiceParameterDefinition.class,
                    adapter.inspect(source).getTarget());
            assertEquals(16, target.getVisibleItemCount());
            assertEquals(16, target.getListVisibleRows());
            assertTrue(io.jenkins.plugins.parameterfoundry.migration.MigrationSnippets.forTarget(target)
                    .contains("visibleItemCount: 16"));
        }
        var source = source(ExtendedChoiceParameterDefinition.PARAMETER_TYPE_CHECK_BOX, "he,mi", "he", null);
        source.setVisibleItemCount(16);
        var target = assertInstanceOf(
                FoundryChoiceParameterDefinition.class, adapter.inspect(source).getTarget());
        assertEquals(0, target.getVisibleItemCount());
    }

    @Test
    void emptyConfigurationRetainsCliParameterPresenceDistinction() {
        for (String raw : new String[] {null, "", " ", ",,"}) {
            var source = source(ExtendedChoiceParameterDefinition.PARAMETER_TYPE_MULTI_SELECT, raw, null, null);
            var target = assertInstanceOf(
                    FoundryChoiceParameterDefinition.class,
                    adapter.inspect(source).getTarget());
            assertTrue(target.getChoices().isEmpty());
            assertEquals(raw == null || raw.isBlank(), target.isLegacyEmptyOptionsOmitCli());
            assertEquals(
                    target.isLegacyEmptyOptionsOmitCli(),
                    io.jenkins.plugins.parameterfoundry.migration.MigrationSnippets.forTarget(target)
                            .contains("legacyEmptyOptionsOmitCli: true"));
        }
    }

    @Test
    void staticSingleChoiceHasDirectMapping() {
        ExtendedChoiceParameterDefinition source =
                source(ExtendedChoiceParameterDefinition.PARAMETER_TYPE_RADIO, "staging,production", "staging", null);

        MigrationPlan plan = adapter.inspect(source);

        assertEquals(MigrationClassification.A, plan.getClassification());
        FoundryChoiceParameterDefinition target =
                assertInstanceOf(FoundryChoiceParameterDefinition.class, plan.getTarget());
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
        FoundryChoiceParameterDefinition target =
                assertInstanceOf(FoundryChoiceParameterDefinition.class, plan.getTarget());
        assertEquals("multiple", target.getSelectionMode());
        assertEquals("checkbox", target.getRenderer());
        assertEquals("extended-choice", target.getStandardApiInputMode());
        assertEquals("deduplicate", target.getDuplicatePolicy());
        String snippet = io.jenkins.plugins.parameterfoundry.migration.MigrationSnippets.forTarget(target);
        assertTrue(snippet.contains("standardApiInputMode: 'extended-choice'"));
        assertTrue(snippet.contains("duplicatePolicy: 'deduplicate'"));
        assertEquals(List.of("web", "android"), target.getDefaultValues());
        assertEquals("web,android", target.getLegacyDefaultLiteral());
        assertTrue(snippet.contains("legacyDefaultLiteral: 'web,android'"));
        assertEquals("submitted", target.getEnvironmentExportOrder());
        assertEquals("submitted source order", plan.getFacts().get("Target export order"));
        assertEquals(1, plan.getDecisions().size());
    }

    @Test
    void emptySourceTransportIsEnabledForLeadingOrInternalButNotTrailingEntries() {
        for (String values : List.of(",a,b", "a,,b", "a, ,b")) {
            var source = source(ExtendedChoiceParameterDefinition.PARAMETER_TYPE_MULTI_SELECT, values, null, null);
            var target = assertInstanceOf(
                    FoundryChoiceParameterDefinition.class,
                    adapter.inspect(source).getTarget());
            assertTrue(target.isLegacyEmptyChoiceAllowed());
            assertEquals(
                    List.of("a", "b"),
                    target.getChoices().stream()
                            .map(option -> option.getValue())
                            .toList());
            assertTrue(io.jenkins.plugins.parameterfoundry.migration.MigrationSnippets.forTarget(target)
                    .contains("legacyEmptyChoiceAllowed: true"));
        }
        var trailing = source(ExtendedChoiceParameterDefinition.PARAMETER_TYPE_SINGLE_SELECT, "a,b,", null, null);
        assertEquals(
                false,
                ((FoundryChoiceParameterDefinition) adapter.inspect(trailing).getTarget())
                        .isLegacyEmptyChoiceAllowed());
    }

    @Test
    void outputDelimiterDoesNotSplitOptionConfigurationOrUiDefaults() {
        var source = source(ExtendedChoiceParameterDefinition.PARAMETER_TYPE_MULTI_SELECT, "a;b,c", "a;b,c", null);
        source.setMultiSelectDelimiter(";");

        var target = assertInstanceOf(
                FoundryChoiceParameterDefinition.class, adapter.inspect(source).getTarget());

        assertEquals(List.of("a;b", "c"), adapter.currentSourceValues(source));
        assertEquals(
                List.of("a;b", "c"),
                target.getChoices().stream().map(option -> option.getValue()).toList());
        assertEquals(List.of("a;b", "c"), target.getDefaultValues());
        assertEquals(";", target.getDelimiter());
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
        FoundryChoiceParameterDefinition target =
                assertInstanceOf(FoundryChoiceParameterDefinition.class, plan.getTarget());
        assertEquals("multiple", target.getSelectionMode());
        assertEquals("none", target.getDefaultMode());
        assertEquals(
                List.of("android", "ios"),
                target.getChoices().stream().map(option -> option.getValue()).toList());
        assertTrue(plan.getDecisions().stream().anyMatch(decision -> decision.contains("empty source option")));
        assertTrue(plan.getDecisions().stream().anyMatch(decision -> decision.contains("non-selectable default")));
    }

    private static ExtendedChoiceParameterDefinition source(
            String type, String values, String defaults, String groovyScript) {
        ExtendedChoiceParameterDefinition source =
                new ExtendedChoiceParameterDefinition("TARGET", type, values, defaults, groovyScript);
        source.setDescription("Synthetic migration fixture");
        return source;
    }
}
