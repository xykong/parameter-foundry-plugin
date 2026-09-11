package io.jenkins.plugins.unifiedparameters.definition;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.DescriptorVisibilityFilter;

/**
 * Keeps the deprecated {@link UnifiedMultiChoiceParameterDefinition} loadable for stored Jobs
 * while removing it from the configuration UI. New parameters use
 * {@link UnifiedChoiceParameterDefinition} with {@code selectionMode: 'multiple'}.
 */
@Extension
public final class LegacyMultipleChoiceVisibilityFilter extends DescriptorVisibilityFilter {

    @Override
    public boolean filter(Object context, Descriptor descriptor) {
        return !(descriptor instanceof UnifiedMultiChoiceParameterDefinition.DescriptorImpl);
    }
}
