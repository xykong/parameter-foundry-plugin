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
    void referenceProducesUnifiedFormSkeleton() {
        var plan = adapter.inspect(new DynamicReferenceParameter("DETAILS"));

        assertTrue(plan.getTargetSkeleton().contains("unifiedForm"));
        assertTrue(plan.getDecisions().stream().anyMatch(decision -> decision.contains("formatted HTML")));
    }
}
