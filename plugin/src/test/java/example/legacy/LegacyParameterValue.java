package example.legacy;

import hudson.model.ParameterValue;
import org.kohsuke.stapler.DataBoundConstructor;

public final class LegacyParameterValue extends ParameterValue {

    private final String value;

    @DataBoundConstructor
    public LegacyParameterValue(String name, String value) {
        super(name);
        this.value = value;
    }

    @Override
    public String getValue() {
        return value;
    }
}
