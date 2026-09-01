package io.jenkins.plugins.unifiedparameters.companion.script;

import hudson.security.Permission;
import hudson.security.PermissionGroup;
import hudson.security.PermissionScope;
import jenkins.model.Jenkins;
import org.jvnet.localizer.Localizable;
import org.jvnet.localizer.ResourceBundleHolder;

public final class ScriptProviderPermissions {

    private static final PermissionGroup GROUP = new PermissionGroup(
            ScriptProviderPermissions.class,
            new Localizable(ResourceBundleHolder.get(ScriptProviderPermissions.class), "PermissionGroup.title"));

    public static final Permission CONFIGURE = new Permission(
            GROUP,
            "Configure",
            new Localizable(
                    ResourceBundleHolder.get(ScriptProviderPermissions.class), "Permission.Configure.description"),
            Jenkins.ADMINISTER,
            PermissionScope.JENKINS);

    private ScriptProviderPermissions() {}
}
