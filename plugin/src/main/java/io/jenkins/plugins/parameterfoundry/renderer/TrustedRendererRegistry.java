package io.jenkins.plugins.parameterfoundry.renderer;

import hudson.PluginWrapper;
import io.jenkins.plugins.parameterfoundry.provider.ParameterFoundryGlobalConfiguration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import jenkins.model.Jenkins;

public final class TrustedRendererRegistry {

    public static final String FAMILY_SINGLE_CHOICE = "single-choice";
    public static final String FAMILY_MULTI_CHOICE = "multi-choice";
    public static final String FAMILY_FORM = "form";

    private static final Map<String, Set<String>> BUILT_INS = Map.of(
            "select", Set.of(FAMILY_SINGLE_CHOICE),
            "searchable", Set.of(FAMILY_SINGLE_CHOICE),
            "radio", Set.of(FAMILY_SINGLE_CHOICE),
            "list", Set.of(FAMILY_MULTI_CHOICE, FAMILY_SINGLE_CHOICE),
            "chips", Set.of(FAMILY_MULTI_CHOICE),
            "checkbox", Set.of(FAMILY_MULTI_CHOICE),
            "schema", Set.of(FAMILY_FORM),
            "accessible-choice-grid", Set.of(FAMILY_SINGLE_CHOICE, FAMILY_MULTI_CHOICE));
    private static final Map<String, List<String>> BUILT_IN_ADJUNCTS =
            Map.of("accessible-choice-grid", List.of("io.jenkins.plugins.parameterfoundry.accessible-choice-grid"));

    private TrustedRendererRegistry() {}

    public static boolean isTrusted(String rendererId, String family) {
        Set<String> builtInFamilies = BUILT_INS.get(rendererId);
        if (builtInFamilies != null) {
            return builtInFamilies.contains(family)
                    && (!"accessible-choice-grid".equals(rendererId)
                            || ParameterFoundryGlobalConfiguration.accessibleChoiceGridFeatureEnabled());
        }
        if (rendererId == null || !rendererId.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")) {
            return false;
        }
        for (FoundryParameterRenderer renderer : FoundryParameterRenderer.all()) {
            if (!rendererId.equals(renderer.getId())
                    || !renderer.getSupportedFamilies().contains(family)
                    || !renderer.getSecurityContract().isTrustedV1()) {
                continue;
            }
            PluginWrapper owner = Jenkins.get().getPluginManager().whichPlugin(renderer.getClass());
            if (owner != null && !"parameter-foundry".equals(owner.getShortName())) {
                return true;
            }
        }
        return false;
    }

    public static List<String> externalRendererIds(String family) {
        java.util.stream.Stream<String> builtIn =
                ParameterFoundryGlobalConfiguration.accessibleChoiceGridFeatureEnabled()
                                && BUILT_INS.get("accessible-choice-grid").contains(family)
                        ? java.util.stream.Stream.of("accessible-choice-grid")
                        : java.util.stream.Stream.empty();
        return java.util.stream.Stream.concat(
                        builtIn,
                        FoundryParameterRenderer.all().stream()
                                .filter(renderer ->
                                        renderer.getSupportedFamilies().contains(family))
                                .filter(renderer ->
                                        renderer.getSecurityContract().isTrustedV1())
                                .filter(renderer -> {
                                    PluginWrapper owner =
                                            Jenkins.get().getPluginManager().whichPlugin(renderer.getClass());
                                    return owner != null && !"parameter-foundry".equals(owner.getShortName());
                                })
                                .map(FoundryParameterRenderer::getId)
                                .filter(id -> id.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,127}"))
                                .filter(id -> !"accessible-choice-grid".equals(id))
                                .distinct()
                                .sorted())
                .toList();
    }

    public static List<String> trustedAdjuncts(String rendererId, String family) {
        if (!isTrusted(rendererId, family)) {
            return List.of();
        }
        if (BUILT_IN_ADJUNCTS.containsKey(rendererId)) {
            return BUILT_IN_ADJUNCTS.get(rendererId);
        }
        if (BUILT_INS.containsKey(rendererId)) {
            return List.of();
        }
        return FoundryParameterRenderer.all().stream()
                .filter(renderer -> rendererId.equals(renderer.getId()))
                .filter(renderer -> renderer.getSupportedFamilies().contains(family))
                .filter(renderer -> renderer.getSecurityContract().isTrustedV1())
                .filter(renderer -> {
                    PluginWrapper owner = Jenkins.get().getPluginManager().whichPlugin(renderer.getClass());
                    return owner != null && !"parameter-foundry".equals(owner.getShortName());
                })
                .flatMap(renderer -> renderer.getSecurityContract().adjuncts().stream())
                .distinct()
                .sorted()
                .toList();
    }

    public static String validationError(String rendererId, String family) {
        return isTrusted(rendererId, family)
                ? null
                : "Renderer is not a trusted " + family + " renderer: " + rendererId;
    }
}
