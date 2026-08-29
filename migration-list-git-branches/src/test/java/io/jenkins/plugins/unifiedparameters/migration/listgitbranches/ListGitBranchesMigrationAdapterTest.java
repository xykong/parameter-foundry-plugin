package io.jenkins.plugins.unifiedparameters.migration.listgitbranches;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.syhuang.hudson.plugins.listgitbranchesparameter.ListGitBranchesParameterDefinition;
import io.jenkins.plugins.unifiedparameters.migration.MigrationClassification;
import org.junit.jupiter.api.Test;

class ListGitBranchesMigrationAdapterTest {

    @Test
    void producesGovernedGitProviderSkeletonWithoutCopyingRepositorySecrets() {
        ListGitBranchesMigrationAdapter adapter = new ListGitBranchesMigrationAdapter();

        var plan = adapter.inspect(new ListGitBranchesParameterDefinition("BRANCH", "PT_BRANCH"));

        assertEquals(MigrationClassification.C, plan.getClassification());
        assertNull(plan.getTarget());
        assertTrue(plan.getTargetSkeleton().contains("TODO-governed-git-refs-provider"));
        assertTrue(plan.getDecisions().stream().anyMatch(decision -> decision.contains("PT_BRANCH")));
        assertTrue(plan.getVerificationFixture().contains("renamed/deleted-ref"));
    }

    @Test
    void organizationBranchModeRemainsAnExplicitMappingDecision() {
        ListGitBranchesMigrationAdapter adapter = new ListGitBranchesMigrationAdapter();

        var plan = adapter.inspect(new ListGitBranchesParameterDefinition("BRANCH", "ORG_BRANCH"));

        assertTrue(plan.getDecisions().stream().anyMatch(decision -> decision.contains("ORG_BRANCH")));
    }
}
