package io.jenkins.plugins.unifiedparameters.migration.activechoices;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.jenkins.plugins.unifiedparameters.migration.MigrationClassification;
import java.util.List;
import org.biouno.unochoice.CascadeChoiceParameter;
import org.biouno.unochoice.ChoiceParameter;
import org.biouno.unochoice.DynamicReferenceParameter;
import org.junit.jupiter.api.Test;

class ActiveChoicesMigrationAdapterTest {

    private final ActiveChoicesMigrationAdapter adapter = new ActiveChoicesMigrationAdapter();

    @Test
    void aConditionalLiteralCascadeBecomesADeclarativeDependencyMapping() {
        CascadeChoiceParameter source = new CascadeChoiceParameter("BUILD_TYPE", "BUILD_MODE");
        source.setScript("if (BUILD_MODE == \"production\") {\n"
                + "    return [\"store\"]\n"
                + "} else {\n"
                + "    return [\"debug\", \"release\"]\n"
                + "}");

        var plan = adapter.inspect(source);

        assertEquals(MigrationClassification.B, plan.getClassification());
        assertNull(plan.getTarget());
        String skeleton = plan.getTargetSkeleton();
        assertTrue(skeleton.contains("unifiedMappingProvider"), skeleton);
        assertTrue(skeleton.contains("dependencyParameter: 'BUILD_MODE'"), skeleton);
        assertTrue(skeleton.contains("[value: 'store']"), skeleton);
        assertTrue(skeleton.contains("[value: 'debug'], [value: 'release']"), skeleton);
        assertTrue(skeleton.contains("dependsOn: ['BUILD_MODE']"), skeleton);
        assertEquals("BUILD_MODE", plan.getFacts().get("Mapping dependency"));
    }

    @Test
    void literalListWithSelectedMarkersBecomesInlineChoices() {
        ChoiceParameter source = new ChoiceParameter("CHANNEL");
        source.setScript("return ['staging', 'production:selected']");
        source.setFilterable(true);

        var plan = adapter.inspect(source);

        assertEquals(MigrationClassification.A, plan.getClassification());
        var target =
                (io.jenkins.plugins.unifiedparameters.definition.UnifiedChoiceParameterDefinition) plan.getTarget();
        assertEquals("single", target.getSelectionMode());
        assertEquals("searchable", target.getRenderer());
        assertEquals("explicit", target.getDefaultMode());
        assertEquals("production", target.getDefaultValue());
        assertEquals(
                List.of("staging", "production"),
                target.getChoices().stream().map(option -> option.getValue()).toList());
        assertEquals("2", plan.getFacts().get("Source options"));
    }

    @Test
    void literalListWithoutMarkersOnlyNeedsTheDefaultDecision() {
        ChoiceParameter source = new ChoiceParameter("CHANNEL");
        source.setScript("return [value: 'a', label: 'A']");

        var plan = adapter.inspect(source);

        assertEquals(MigrationClassification.B, plan.getClassification());
        assertTrue(plan.getDecisions().stream().anyMatch(decision -> decision.contains("preselects the first option")));
    }

    @Test
    void networkScriptProducesRestProviderGuidanceWithRedactedEndpoint() {
        ChoiceParameter source = new ChoiceParameter("PLATFORMS");
        source.setScript("def response = new URL('https://internal.example.test/api/platforms?env=prod').getText()\n"
                + "def json = new groovy.json.JsonSlurper().parseText(response)\n"
                + "return json.collect { [value: it.value, label: it.label] }");

        var plan = adapter.inspect(source);

        assertEquals(MigrationClassification.C, plan.getClassification());
        assertTrue(plan.getTargetSkeleton().contains("unifiedRestProvider"));
        assertTrue(plan.getTargetSkeleton().contains("relativePath: '<reviewed-source-path>'"));
        assertFalse(plan.getTargetSkeleton().contains("internal.example.test"));
        assertEquals("https://<host>/api/platforms", plan.getFacts().get("Detected endpoint"));
        assertTrue(plan.getFacts().get("Detected response fields").contains("value"));
        assertTrue(plan.getDecisions().stream().anyMatch(decision -> decision.contains("REST Provider")));
    }

    @Test
    void controllerScriptIsAnExplicitRedesign() {
        ChoiceParameter source = new ChoiceParameter("PLATFORMS");
        source.setScript("return Jenkins.instance.getItem('seed').getBuilds().collect { it.number }");

        var plan = adapter.inspect(source);

        assertEquals(MigrationClassification.D, plan.getClassification());
        assertNull(plan.getTargetSkeleton());
        assertTrue(plan.getDecisions().stream().anyMatch(decision -> decision.contains("controller")));
    }

    @Test
    void choiceProducesGovernedProviderSkeletonWithoutExecutingScript() {
        var plan = adapter.inspect(new ChoiceParameter("CHANNEL"));

        assertEquals(MigrationClassification.C, plan.getClassification());
        assertNull(plan.getTarget());
        assertTrue(plan.getTargetSkeleton().contains("providerId: 'TODO-governed-provider'"));
        assertTrue(plan.getVerificationFixture().contains("Typed API"));
    }

    @Test
    void cascadeCarriesDeclaredDependenciesIntoSkeleton() {
        var plan = adapter.inspect(new CascadeChoiceParameter("ARTIFACT", "ENVIRONMENT, REGION"));

        assertTrue(plan.getTargetSkeleton().contains("dependsOn: ['ENVIRONMENT', 'REGION']"));
    }

    @Test
    void textBoxReferenceProducesTypedStringFieldAndPreservesDependencies() {
        var plan = adapter.inspect(new DynamicReferenceParameter("DETAILS", "ENVIRONMENT", "ET_TEXT_BOX", false));

        assertTrue(plan.getTargetSkeleton().contains("unifiedForm"));
        assertTrue(plan.getTargetSkeleton().contains("field('DETAILS', 'string')"));
        assertTrue(plan.getTargetSkeleton().contains("ENVIRONMENT"));
        assertTrue(plan.getDecisions().stream().anyMatch(decision -> decision.contains("raw string")));
    }

    @Test
    void listReferenceMakesPresentationOnlySemanticsExplicit() {
        var plan = adapter.inspect(new DynamicReferenceParameter("DETAILS", "", "ET_ORDERED_LIST", true));

        assertEquals(MigrationClassification.C, plan.getClassification());
        assertTrue(plan.getTargetSkeleton().contains("field('DETAILS', 'content')"));
        assertTrue(plan.getDecisions().stream().anyMatch(decision -> decision.contains("presentation-only")));
        assertTrue(plan.getDecisions().stream().anyMatch(decision -> decision.contains("omits its value field")));
    }

    @Test
    void formattedReferenceProducesTypedFormSkeletonWithoutMarkup() {
        var plan = adapter.inspect(new DynamicReferenceParameter("DETAILS"));

        assertEquals(MigrationClassification.C, plan.getClassification());
        assertTrue(plan.getTargetSkeleton().contains("unifiedForm"));
        assertTrue(plan.getTargetSkeleton().contains("TODO reviewed typed fields"));
        assertTrue(plan.getDecisions().stream().anyMatch(decision -> decision.contains("formatted HTML")));
    }

    @Test
    void hiddenFormattedReferenceRequiresManualClassDDecision() {
        var plan = adapter.inspect(
                new DynamicReferenceParameter("DETAILS", "ENVIRONMENT", "ET_FORMATTED_HIDDEN_HTML", true));

        assertEquals(MigrationClassification.D, plan.getClassification());
        assertTrue(plan.getReason().contains("no portable typed value contract"));
        assertTrue(plan.getDecisions().stream().anyMatch(decision -> decision.contains("manual class D")));
        assertNull(plan.getTargetSkeleton());
    }

    @Test
    void unknownReferenceModeDoesNotGuessAFormContract() {
        var plan = adapter.inspect(new DynamicReferenceParameter("DETAILS", "", "ET_CUSTOM", false));

        assertEquals(MigrationClassification.D, plan.getClassification());
        assertNull(plan.getTargetSkeleton());
        assertTrue(plan.getReason().contains("no proven typed contract"));
    }
}
