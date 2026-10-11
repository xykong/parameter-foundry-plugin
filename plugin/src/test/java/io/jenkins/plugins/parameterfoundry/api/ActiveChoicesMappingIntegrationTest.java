package io.jenkins.plugins.parameterfoundry.api;

import static org.junit.jupiter.api.Assertions.*;

import hudson.cli.CLICommand;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.StringParameterDefinition;
import io.jenkins.plugins.parameterfoundry.definition.FoundryChoiceParameterDefinition;
import io.jenkins.plugins.parameterfoundry.model.Option;
import io.jenkins.plugins.parameterfoundry.provider.MappingCase;
import io.jenkins.plugins.parameterfoundry.provider.MappingProviderDefinition;
import io.jenkins.plugins.parameterfoundry.provider.ParameterFoundryGlobalConfiguration;
import java.net.URL;
import java.util.List;
import net.sf.json.JSONObject;
import org.htmlunit.HttpMethod;
import org.htmlunit.WebRequest;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class ActiveChoicesMappingIntegrationTest {
    @Test
    void mappingUiDefaultsRemainIndependentFromLiteralApiDefaults(JenkinsRule j) throws Exception {
        var provider = new MappingProviderDefinition("encoding-by-mode", "MODE");
        provider.setDefaultOptions(List.of(new Option("false"), new Option("true")));
        var production = new MappingCase("production");
        production.setOptions(List.of(new Option("true"), new Option("false")));
        provider.setCases(List.of(production));
        provider.setCacheTtlSeconds(0);
        ParameterFoundryGlobalConfiguration.get().setProviders(List.of(provider));
        var child = new FoundryChoiceParameterDefinition("ENCODING");
        child.setProviderId(provider.getId());
        child.setDependsOn(List.of("MODE"));
        child.setRenderer("select");
        child.setDefaultMode("first");
        child.setStandardApiInputMode("active-choices");
        child.setActiveChoicesApiDefaultMode("literal");
        child.setActiveChoicesApiDefaultLiteral("false");
        var job = j.createFreeStyleProject();
        job.addProperty(new ParametersDefinitionProperty(new StringParameterDefinition("MODE", "development"), child));
        j.jenkins.setCrumbIssuer(null);
        for (String mode : List.of("development", "production", "development", "unknown")) {
            var body = new JSONObject();
            body.put("parameter", "ENCODING");
            body.put("dependencies", JSONObject.fromObject("{\"MODE\":\"" + mode + "\"}"));
            var response = resolve(j, job.getUrl(), body);
            assertEquals(200, response.getStatusCode());
            var resolved = response.getJSONObject();
            assertTrue(resolved.getBoolean("ok"));
            assertFalse(resolved.containsKey("filterMode"));
            var expected = mode.equals("production") ? List.of("true", "false") : List.of("false", "true");
            assertEquals(expected.get(0), resolved.getString("default"));
            assertEquals(
                    expected.get(0),
                    resolved.getJSONArray("options").getJSONObject(0).getString("value"));
            assertEquals(
                    expected.get(1),
                    resolved.getJSONArray("options").getJSONObject(1).getString("value"));
            assertEquals("false", child.getDefaultParameterValue().getValue());
        }
        assertEquals(
                " custom ", child.createValue((CLICommand) null, " custom ").getValue());
        var absent = new JSONObject();
        absent.put("parameter", "ENCODING");
        var failure = resolve(j, job.getUrl(), absent);
        assertEquals(400, failure.getStatusCode());
        assertEquals(
                "PARAMETER_FOUNDRY_DEPENDENCY_INVALID",
                failure.getJSONObject().getJSONArray("errors").getJSONObject(0).getString("code"));
        j.configRoundtrip(job);
        var reloaded = (FoundryChoiceParameterDefinition)
                job.getProperty(ParametersDefinitionProperty.class).getParameterDefinition("ENCODING");
        assertEquals("false", reloaded.getDefaultParameterValue().getValue());
    }

    private JenkinsRule.JSONWebResponse resolve(JenkinsRule j, String jobUrl, JSONObject body) throws Exception {
        var request = new WebRequest(new URL(j.getURL(), jobUrl + "parameter-foundry/api/v1/resolve"), HttpMethod.POST);
        request.setAdditionalHeader("Content-Type", "application/json");
        request.setRequestBody(body.toString());
        return new JenkinsRule.JSONWebResponse(j.createWebClient().loadWebResponse(request));
    }
}
