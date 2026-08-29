package io.jenkins.plugins.unifiedparameters.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.jenkins.plugins.unifiedparameters.model.Option;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class StructuredOptionParserTest {

    @Test
    void mapsJsonArrayFieldsToOptions() {
        List<Option> options = StructuredOptionParser.parse(
                """
                {
                  "items": [
                    {
                      "id": "android",
                      "title": "Android",
                      "metadata": {"group": "Mobile"},
                      "disabled": false,
                      "tags": ["device", "release"]
                    },
                    {
                      "id": "legacy",
                      "title": "Legacy",
                      "metadata": {"group": "Retired"},
                      "disabled": true,
                      "reason": "Unsupported"
                    }
                  ]
                }
                """.getBytes(StandardCharsets.UTF_8),
                "json",
                "$.items[*]",
                "id",
                "title",
                "",
                "metadata.group",
                "disabled",
                "reason",
                "tags",
                "json");

        assertEquals(
                List.of("android", "legacy"),
                options.stream().map(Option::getValue).toList());
        assertEquals("Android", options.get(0).getLabel());
        assertEquals("Mobile", options.get(0).getGroup());
        assertEquals(List.of("device", "release"), options.get(0).getTags());
        assertTrue(options.get(1).isDisabled());
        assertEquals("Unsupported", options.get(1).getDisabledReason());
    }

    @Test
    void mapsSecureXmlAndRejectsDoctype() {
        List<Option> options = StructuredOptionParser.parse(
                """
                <options>
                  <option value="ios">
                    <label>iOS</label>
                    <group>Mobile</group>
                    <tags>device,release</tags>
                  </option>
                </options>
                """.getBytes(StandardCharsets.UTF_8),
                "xml",
                "/options/option",
                "@value",
                "label",
                "",
                "group",
                "",
                "",
                "tags",
                "xml");

        assertEquals("ios", options.get(0).getValue());
        assertEquals("iOS", options.get(0).getLabel());
        assertEquals(List.of("device", "release"), options.get(0).getTags());

        assertThrows(
                ProviderException.class,
                () -> StructuredOptionParser.parse(
                        """
                        <!DOCTYPE options [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
                        <options><option value="&xxe;"/></options>
                        """.getBytes(StandardCharsets.UTF_8),
                        "xml",
                        "/options/option",
                        "@value",
                        "",
                        "",
                        "",
                        "",
                        "",
                        "",
                        "xml"));
    }
}
