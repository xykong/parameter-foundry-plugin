package io.jenkins.plugins.unifiedparameters.renderer;

import java.util.Set;

public record RendererSecurityContract(
        int apiVersion,
        boolean cspSafe,
        boolean escapesUntrustedContent,
        boolean usesInlineScript,
        boolean acceptsJobHtml,
        Set<String> adjuncts) {

    public RendererSecurityContract {
        adjuncts = adjuncts == null ? Set.of() : Set.copyOf(adjuncts);
    }

    public boolean isTrustedV1() {
        return apiVersion == 1
                && cspSafe
                && escapesUntrustedContent
                && !usesInlineScript
                && !acceptsJobHtml
                && adjuncts.stream().allMatch(value -> value.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,255}"));
    }
}
