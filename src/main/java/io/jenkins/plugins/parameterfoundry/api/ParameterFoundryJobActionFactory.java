package io.jenkins.plugins.parameterfoundry.api;

import hudson.Extension;
import hudson.model.Action;
import hudson.model.Job;
import hudson.model.ParametersDefinitionProperty;
import java.util.Collection;
import java.util.List;
import jenkins.model.TransientActionFactory;

@Extension
public final class ParameterFoundryJobActionFactory extends TransientActionFactory<Job> {

    @Override
    public Class<Job> type() {
        return Job.class;
    }

    @Override
    public Class<? extends Action> actionType() {
        return ParameterFoundryJobAction.class;
    }

    @Override
    public Collection<? extends Action> createFor(Job target) {
        if (target.getProperty(ParametersDefinitionProperty.class) == null) {
            return List.of();
        }
        return List.of(new ParameterFoundryJobAction(target));
    }
}
