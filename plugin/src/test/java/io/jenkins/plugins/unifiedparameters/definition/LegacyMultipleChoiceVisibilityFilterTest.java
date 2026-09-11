package io.jenkins.plugins.unifiedparameters.definition;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.model.Descriptor;
import hudson.model.DescriptorVisibilityFilter;
import hudson.model.FreeStyleProject;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class LegacyMultipleChoiceVisibilityFilterTest {

    @Test
    void legacyDescriptorIsHiddenWhileUnifiedEntryStaysVisible(JenkinsRule jenkins) throws Exception {
        FreeStyleProject job = jenkins.createFreeStyleProject("visibility");
        Descriptor<?> legacy =
                jenkins.jenkins.getDescriptorByType(UnifiedMultiChoiceParameterDefinition.DescriptorImpl.class);
        Descriptor<?> unified =
                jenkins.jenkins.getDescriptorByType(UnifiedChoiceParameterDefinition.DescriptorImpl.class);

        List<Descriptor<?>> visible = DescriptorVisibilityFilter.apply(job, List.of(legacy, unified));

        assertFalse(visible.contains(legacy));
        assertTrue(visible.contains(unified));
    }
}
