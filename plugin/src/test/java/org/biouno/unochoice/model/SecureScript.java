package org.biouno.unochoice.model;

/** Test double for the script-security wrapper that carries the script body. */
public final class SecureScript {

    private final String script;

    public SecureScript(String script) {
        this.script = script;
    }

    public String getScript() {
        return script;
    }
}
