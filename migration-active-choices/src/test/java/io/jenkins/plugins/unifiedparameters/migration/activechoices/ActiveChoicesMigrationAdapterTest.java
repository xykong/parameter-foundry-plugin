package io.jenkins.plugins.unifiedparameters.migration.activechoices;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.jenkins.plugins.unifiedparameters.migration.MigrationClassification;
import org.biouno.unochoice.CascadeChoiceParameter;
import org.biouno.unochoice.ChoiceParameter;
import org.biouno.unochoice.DynamicReferenceParameter;
import org.junit.jupiter.api.Test;

class ActiveChoicesMigrationAdapterTest {

    private final ActiveChoicesMigrationAdapter adapter = new ActiveChoicesMigrationAdapter();

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
