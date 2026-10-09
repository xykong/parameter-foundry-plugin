package io.jenkins.plugins.parameterfoundry.migration.listgitbranches;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.syhuang.hudson.plugins.listgitbranchesparameter.ListGitBranchesParameterDefinition;
import io.jenkins.plugins.parameterfoundry.migration.MigrationClassification;
import org.junit.jupiter.api.Test;

class ListGitBranchesMigrationAdapterTest {

    @Test
    void producesGovernedGitProviderSkeletonWithoutCopyingRepositorySecrets() {
        ListGitBranchesMigrationAdapter adapter = new ListGitBranchesMigrationAdapter();

        var plan = adapter.inspect(new ListGitBranchesParameterDefinition(
                "BRANCH", "PT_BRANCH", "https://example.test/repository.git", "secret-id"));

        assertEquals(MigrationClassification.C, plan.getClassification());
        assertNull(plan.getTarget());
        assertTrue(plan.getTargetSkeleton().contains("foundryGitRefProvider"));
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

    @Test
    void translatesSourceFiltersIntoShortNamePatterns() {
        assertEquals(
                "(?!release/).*",
                ListGitBranchesMigrationAdapter.translateFilter(".*refs/heads/(?!release/)(.*)", "branch"));
        assertEquals("v.*", ListGitBranchesMigrationAdapter.translateFilter("refs/tags/v.*", "tag"));
        assertEquals("main|develop", ListGitBranchesMigrationAdapter.translateFilter("main|develop", "branch"));
        assertNull(ListGitBranchesMigrationAdapter.translateFilter("*", "branch"));
        assertNull(ListGitBranchesMigrationAdapter.translateFilter("", "branch"));
        assertNull(ListGitBranchesMigrationAdapter.translateFilter("refs/heads/dev|refs/tags/x", "branch"));
    }

    @Test
    void mapsSourceSortModesAndKeepsThemInTheSkeleton() {
        assertEquals("smart-descending", ListGitBranchesMigrationAdapter.mapSortOrder("DESCENDING_SMART"));
        assertEquals("smart-ascending", ListGitBranchesMigrationAdapter.mapSortOrder("ASCENDING_SMART"));
        assertEquals("ascending", ListGitBranchesMigrationAdapter.mapSortOrder("NONE"));
        assertNull(ListGitBranchesMigrationAdapter.mapSortOrder("SOMETHING_ELSE"));

        ListGitBranchesParameterDefinition source = new ListGitBranchesParameterDefinition("REF", "PT_BRANCH");
        source.setBranchFilter(".*refs/heads/(?!release/)(.*)");
        source.setSortMode("DESCENDING_SMART");
        source.setDefaultValue("master");
        source.setSelectedValue("TOP");
        source.setQuickFilterEnabled(true);

        var plan = new ListGitBranchesMigrationAdapter().inspect(source);

        assertTrue(plan.getTargetSkeleton().contains("includePattern: '(?!release/).*'"));
        assertTrue(plan.getTargetSkeleton().contains("sortOrder: 'smart-descending'"));
        assertTrue(plan.getTargetSkeleton().contains("renderer: 'searchable'"));
        assertTrue(plan.getTargetSkeleton().contains("standardApiInputMode: 'list-git-branches'"));
        assertTrue(plan.getTargetSkeleton().contains("defaultMode: 'explicit'"));
        assertTrue(plan.getTargetSkeleton().contains("defaultValue: 'master'"));
        assertTrue(plan.getTargetSkeleton().contains("gitSelectionMode: 'top'"));
        assertEquals("DESCENDING_SMART", plan.getFacts().get("Source sort"));
        assertEquals("smart-descending", plan.getFacts().get("Target sort"));
        assertEquals("master", plan.getFacts().get("Source default"));
        assertTrue(plan.getDecisions().stream().anyMatch(decision -> decision.contains("smart-descending")));
        assertTrue(plan.getDecisions().stream().anyMatch(decision -> decision.contains("Source default master")));
        assertTrue(plan.getDecisions().stream().anyMatch(decision -> decision.contains("SelectedValue.TOP")));
    }

    @Test
    void anInvalidSourceFilterIsReportedInsteadOfCopied() {
        // A source fork filter such as .*refs/heads/(*) is not a valid full-match regex.
        assertNull(ListGitBranchesMigrationAdapter.translateFilter(".*refs/heads/(*)", "branch"));
        assertNull(ListGitBranchesMigrationAdapter.translateFilter("refs/heads/(*)/x", "branch"));
    }

    @Test
    void preservesNullStoredDefaultRatherThanConvertingItToEmpty() {
        var source = new ListGitBranchesParameterDefinition("REF", "PT_BRANCH");
        source.setDefaultValue(null);
        source.setSelectedValue("TOP");
        var plan = new ListGitBranchesMigrationAdapter().inspect(source);
        assertTrue(plan.getTargetSkeleton().contains("defaultValue: null"));
        assertTrue(plan.getTargetSkeleton().contains("defaultMode: 'first'"));
    }
}
