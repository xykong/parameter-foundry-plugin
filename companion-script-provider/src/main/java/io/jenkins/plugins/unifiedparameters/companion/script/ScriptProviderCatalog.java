package io.jenkins.plugins.unifiedparameters.companion.script;

import hudson.Extension;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.verb.POST;

@Extension
public final class ScriptProviderCatalog extends GlobalConfiguration {

    private List<ScriptCatalogEntry> entries = List.of();

    public ScriptProviderCatalog() {
        load();
    }

    public static ScriptProviderCatalog get() {
        return GlobalConfiguration.all().get(ScriptProviderCatalog.class);
    }

    public List<ScriptCatalogEntry> getEntries() {
        return entries;
    }

    @DataBoundSetter
    public void setEntries(List<ScriptCatalogEntry> entries) {
        this.entries = entries == null ? List.of() : List.copyOf(new ArrayList<>(entries));
    }

    public Optional<ScriptCatalogEntry> findEntry(String sourceReference) {
        if (sourceReference == null || !sourceReference.startsWith("catalog:")) {
            return Optional.empty();
        }
        String id = sourceReference.substring("catalog:".length());
        if (!id.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")) {
            return Optional.empty();
        }
        return entries.stream().filter(entry -> id.equals(entry.getId())).findFirst();
    }

    @POST
    @Override
    public boolean configure(StaplerRequest2 request, net.sf.json.JSONObject json)
            throws hudson.model.Descriptor.FormException {
        Jenkins.get().checkPermission(ScriptProviderPermissions.CONFIGURE);
        return super.configure(request, json);
    }

    @Override
    public hudson.security.Permission getRequiredGlobalConfigPagePermission() {
        return ScriptProviderPermissions.CONFIGURE;
    }
}
