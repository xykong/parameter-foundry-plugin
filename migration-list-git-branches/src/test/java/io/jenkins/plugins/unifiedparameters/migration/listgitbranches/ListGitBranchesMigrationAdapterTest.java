package io.jenkins.plugins.unifiedparameters.migration.listgitbranches;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.syhuang.hudson.plugins.listgitbranchesparameter.ListGitBranchesParameterDefinition;
import io.jenkins.plugins.unifiedparameters.migration.MigrationClassification;
import org.junit.jupiter.api.Test;

class ListGitBranchesMigrationAdapterTest {

    @Test
    void producesGovernedGitProviderSkeletonWithoutCopyingRepositorySecrets() {
        ListGitBranchesMigrationAdapter adapter = new ListGitBranchesMigrationAdapter();

        var plan = adapter.inspect(new ListGitBranchesParameterDefinition(
                "BRANCH", "PT_BRANCH", "https://example.test/repository.git", "secret-id"));

        assertEquals(MigrationClassification.C, plan.getClassification());
        assertNull(plan.getTarget());
        assertTrue(plan.getTargetSkeleton().contains("unifiedGitRefProvider"));
        assertTrue(plan.getTargetSkeleton().contains("refType: 'branch'"));
        assertTrue(plan.getTargetSkeleton().contains("connectionProfileId"));
        assertFalse(plan.getTargetSkeleton().contains("example.test"));
        assertFalse(plan.getTargetSkeleton().contains("secret-id"));
        assertTrue(plan.getDecisions().stream().anyMatch(decision -> decision.contains("PT_BRANCH")));
        assertTrue(plan.getVerificationFixture().contains("renamed/deleted-ref"));
    }

    @Test
    void mapsTagAndCombinedModesToTheirCanonicalGitRefTypes() {
        ListGitBranchesMigrationAdapter adapter = new ListGitBranchesMigrationAdapter();

        var tag = adapter.inspect(new ListGitBranchesParameterDefinition("TAG", "PT_TAG"));
        assertTrue(tag.getTargetSkeleton().contains("refType: 'tag'"));

        var combined = adapter.inspect(new ListGitBranchesParameterDefinition("REF", "PT_BRANCH_TAG"));
        assertTrue(combined.getTargetSkeleton().contains("refType: 'ref'"));
        assertTrue(combined.getTargetSkeleton().contains("refs/(heads|tags)/.*"));
        assertTrue(combined.getDecisions().stream().anyMatch(decision -> decision.contains("full canonical refs")));
    }

    @Test
    void organizationBranchModeRequiresOutputAndAuthorityConfirmation() {
        ListGitBranchesMigrationAdapter adapter = new ListGitBranchesMigrationAdapter();

        var plan = adapter.inspect(new ListGitBranchesParameterDefinition("BRANCH", "ORG_BRANCH"));

        assertEquals(MigrationClassification.C, plan.getClassification());
        assertTrue(plan.getTargetSkeleton().contains("refType: 'branch'"));
        assertTrue(plan.getDecisions().stream().anyMatch(decision -> decision.contains("ORG_BRANCH")));
        assertTrue(plan.getDecisions().stream().anyMatch(decision -> decision.contains("one branch string")));
    }

    @Test
    void unknownModeDoesNotFallBackToBranches() {
        ListGitBranchesMigrationAdapter adapter = new ListGitBranchesMigrationAdapter();

        var plan = adapter.inspect(new ListGitBranchesParameterDefinition("REF", "CUSTOM_MODE"));

        assertEquals(MigrationClassification.D, plan.getClassification());
        assertTrue(plan.getReason().contains("CUSTOM_MODE"));
        assertNull(plan.getTargetSkeleton());
    }
}
