package io.jenkins.plugins.unifiedparameters;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.jenkins.plugins.unifiedparameters.provider.extension.ScriptProviderSecurityContract;
import io.jenkins.plugins.unifiedparameters.renderer.RendererSecurityContract;
import io.jenkins.plugins.unifiedparameters.renderer.TrustedRendererRegistry;
import io.jenkins.plugins.unifiedparameters.renderer.UnifiedParameterRenderer;
import java.lang.reflect.Method;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ExtensionContractTest {

    @Test
    void builtInRenderersAreTrustedAndUnsafeContractsFailClosed() {
        assertTrue(TrustedRendererRegistry.isTrusted("schema", TrustedRendererRegistry.FAMILY_FORM));
        assertTrue(TrustedRendererRegistry.isTrusted("chips", TrustedRendererRegistry.FAMILY_MULTI_CHOICE));

        RendererSecurityContract unsafe =
                new RendererSecurityContract(1, true, true, true, false, Set.of("example.renderer"));
        assertFalse(unsafe.isTrustedV1());
        RendererSecurityContract incompatible =
                new RendererSecurityContract(2, true, true, false, false, Set.of("example.renderer"));
        assertFalse(incompatible.isTrustedV1());
    }

    @Test
    void rendererSpiCannotReturnRawMarkupOrScriptSource() {
        for (Method method : UnifiedParameterRenderer.class.getDeclaredMethods()) {
            assertFalse(
                    method.getName().toLowerCase().contains("html")
                            || method.getName().toLowerCase().contains("javascript"),
                    method.toString());
        }
    }

    @Test
    void scriptProviderContractRequiresSeparatePermissionAuditAndSchemaControls() {
        ScriptProviderSecurityContract reviewed =
                new ScriptProviderSecurityContract(1, true, true, true, true, true, true, Set.of("scm"));
        assertTrue(reviewed.isReviewedV1());
        ScriptProviderSecurityContract missingAudit =
                new ScriptProviderSecurityContract(1, true, true, false, true, true, true, Set.of("scm"));
        assertFalse(missingAudit.isReviewedV1());
    }
}
