package example.legacy;

import hudson.cli.CLICommand;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import java.io.IOException;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest2;

public final class LegacyParameterDefinition extends ParameterDefinition {

    @DataBoundConstructor
    public LegacyParameterDefinition(String name) {
        super(name);
    }

    @Override
    public ParameterValue createValue(StaplerRequest2 request) {
        return new LegacyParameterValue(getName(), request.getParameter(getName()));
    }

    @Override
    public ParameterValue createValue(StaplerRequest2 request, JSONObject submitted) {
        return new LegacyParameterValue(getName(), submitted.optString("value"));
    }

    @Override
    public ParameterValue createValue(CLICommand command, String value) throws IOException, InterruptedException {
        return new LegacyParameterValue(getName(), value);
    }

    @Override
    public ParameterValue getDefaultParameterValue() {
        return new LegacyParameterValue(getName(), "legacy");
    }
}
