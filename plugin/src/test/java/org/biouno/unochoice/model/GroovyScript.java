package org.biouno.unochoice.model;

/** Test double for the Active Choices script model object. */
public final class GroovyScript {

    private final SecureScript script;

    public GroovyScript(SecureScript script) {
        this.script = script;
    }

    public SecureScript getScript() {
        return script;
    }

    public SecureScript getFallbackScript() {
        return script;
    }

    @Override
    public String toString() {
        return "org.biouno.unochoice.model.GroovyScript@" + Integer.toHexString(hashCode());
    }
}
