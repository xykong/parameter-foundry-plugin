package io.jenkins.plugins.unifiedparameters.companion.script;

import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import java.io.Serial;
import java.io.Serializable;
import org.jenkinsci.plugins.scriptsecurity.sandbox.groovy.SecureGroovyScript;
import org.kohsuke.stapler.DataBoundConstructor;

public final class ScriptCatalogEntry extends AbstractDescribableImpl<ScriptCatalogEntry> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String id;
    private final SecureGroovyScript script;

    @DataBoundConstructor
    public ScriptCatalogEntry(String id, SecureGroovyScript script) throws Descriptor.FormException {
        if (id == null || !id.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")) {
            throw new Descriptor.FormException(
                    "Script ID must be 1-128 characters using letters, digits, dot, underscore, or hyphen.", "id");
        }
        if (script == null) {
            throw new Descriptor.FormException("A sandboxed script is required.", "script");
        }
        if (!script.isSandbox()) {
            throw new Descriptor.FormException("Non-sandboxed scripts are not supported by this companion.", "script");
        }
        this.id = id;
        this.script = script;
    }

    public String getId() {
        return id;
    }

    public SecureGroovyScript getScript() {
        return script;
    }
}
