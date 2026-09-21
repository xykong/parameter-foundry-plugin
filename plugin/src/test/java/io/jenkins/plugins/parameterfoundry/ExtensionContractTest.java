package io.jenkins.plugins.parameterfoundry;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.jenkins.plugins.parameterfoundry.provider.extension.ScriptProviderSecurityContract;
import io.jenkins.plugins.parameterfoundry.renderer.FoundryParameterRenderer;
import io.jenkins.plugins.parameterfoundry.renderer.RendererSecurityContract;
import io.jenkins.plugins.parameterfoundry.renderer.TrustedRendererRegistry;
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
        for (Method method : FoundryParameterRenderer.class.getDeclaredMethods()) {
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
        ScriptProviderSecurityContract builtIn =
                new ScriptProviderSecurityContract(1, false, true, true, true, true, true, Set.of("catalog"));
        assertTrue(builtIn.isReviewedBuiltInV1());
        ScriptProviderSecurityContract missingAudit =
                new ScriptProviderSecurityContract(1, true, true, false, true, true, true, Set.of("scm"));
        assertFalse(missingAudit.isReviewedV1());
    }
}
