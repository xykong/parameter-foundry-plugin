package io.jenkins.plugins.parameterfoundry.definition;

import hudson.Extension;
import hudson.markup.RawHtmlMarkupFormatter;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.model.SimpleParameterDefinition;
import hudson.model.StringParameterValue;
import io.jenkins.plugins.parameterfoundry.evaluation.ActiveChoicesCompatibility;
import java.io.IOException;
import java.io.Serial;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.StaplerRequest2;

/** Sanitized literal presentation with an independent native string transport. */
public final class FoundryReferenceParameterDefinition extends SimpleParameterDefinition {
    @Serial
    private static final long serialVersionUID = 1L;

    private String html;

    @DataBoundConstructor
    public FoundryReferenceParameterDefinition(String name) {
        super(name);
    }

    public String getHtml() {
        return html == null ? "" : html;
    }

    @DataBoundSetter
    public void setHtml(String html) {
        if (html != null && html.length() > 65536) {
            throw new IllegalArgumentException("Reference HTML must not exceed 65536 characters.");
        }
        this.html = html;
    }

    public String getSanitizedHtml() throws IOException {
        if (getHtml().length() > 65536) {
            throw new IllegalArgumentException("Reference HTML must not exceed 65536 characters.");
        }
        return RawHtmlMarkupFormatter.INSTANCE.translate(getHtml());
    }

    @Override
    public ParameterValue createValue(String value) {
        return new StringParameterValue(getName(), ActiveChoicesCompatibility.cli(value), getDescription());
    }

    @Override
    public ParameterValue createValue(StaplerRequest2 request, JSONObject submitted) {
        return createValue(ActiveChoicesCompatibility.form(submitted));
    }

    @Override
    public ParameterValue getDefaultParameterValue() {
        return new StringParameterValue(getName(), "");
    }

    @Override
    public ParameterDefinition copyWithDefaultValue(ParameterValue value) {
        // Native literal presentation references do not derive their hidden field from history.
        return this;
    }

    @Extension
    @Symbol("foundryReference")
    public static final class DescriptorImpl extends ParameterDescriptor {
        @Override
        public String getDisplayName() {
            return "Foundry Reference";
        }
    }
}
