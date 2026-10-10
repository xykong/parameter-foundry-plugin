package io.jenkins.plugins.parameterfoundry.provider;

import static org.junit.jupiter.api.Assertions.*;

import groovy.json.JsonSlurper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SourceJsonTextTest {
    @Test
    void numericJsonLabelsMatchNativeJavaScriptNumberStrings() throws Exception {
        try (var stream =
                getClass().getResourceAsStream("/io/jenkins/plugins/parameterfoundry/javascript-number-text.json")) {
            var cases =
                    (List<?>) new JsonSlurper().parseText(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            assertEquals(513, cases.size());
            for (Object item : cases) {
                var row = (Map<?, ?>) item;
                assertEquals(
                        row.get("expected"),
                        SourceJsonText.javascript(Double.parseDouble((String) row.get("number"))),
                        row.toString());
            }
        }
        assertEquals("0", SourceJsonText.javascript(new BigDecimal("1E-1000")));
        assertEquals("Infinity", SourceJsonText.javascript(new BigDecimal("1E1000")));
    }

    @Test
    void arraysObjectsAndNullUseDataOnlyJavaScriptCoercion() {
        assertEquals("", SourceJsonText.javascript(List.of()));
        assertEquals(
                ",alpha,,[object Object]",
                SourceJsonText.javascript(Arrays.asList(null, "alpha", List.of(), Map.of("x", 1))));
        assertEquals("null", SourceJsonText.javascript(null));
        assertEquals("true", SourceJsonText.javascript(true));
        assertEquals("[object Object]", SourceJsonText.javascript(Map.of("valueOf", false)));
        assertThrows(IllegalArgumentException.class, () -> SourceJsonText.javascript(Map.of("toString", "label")));
        assertThrows(
                IllegalArgumentException.class,
                () -> SourceJsonText.javascript(List.of(Map.of("toString", List.of()))));
    }

    @Test
    void boundedResponsesKeepLargeNumericAndNestedArrayLabelsWithinResources() {
        assertTimeout(java.time.Duration.ofSeconds(30), () -> {
            var labels = new java.util.LinkedHashMap<String, Object>();
            for (int index = 0; index < 10_000; index++) {
                labels.put(
                        "value-" + index,
                        List.of(
                                new BigDecimal("1.7976931348623157E308"),
                                new BigDecimal("4.9406564584124654E-324"),
                                List.of(index, true)));
            }
            var json = groovy.json.JsonOutput.toJson(Map.of("data", labels));
            var choices = ActiveChoicesChoiceResponse.options(json.getBytes(StandardCharsets.UTF_8), true, "large");
            assertEquals(10_000, choices.size());
            assertTrue(choices.get(0).getLabel().startsWith("1.7976931348623157e+308,5e-324,"));
            String nested = "{\"data\":{\"value\":" + "[".repeat(62) + "1" + "]".repeat(62) + "}}";
            assertEquals(
                    "1",
                    ActiveChoicesChoiceResponse.options(nested.getBytes(StandardCharsets.UTF_8), true, "deep")
                            .get(0)
                            .getLabel());
            String boundary = "{\"data\":{\"value\":[1,null]}}";
            boundary += " ".repeat(10 * 1024 * 1024 - boundary.length());
            assertEquals(
                    "1,",
                    ActiveChoicesChoiceResponse.options(boundary.getBytes(StandardCharsets.UTF_8), true, "bytes")
                            .get(0)
                            .getLabel());
            String overflow = boundary + " ";
            assertThrows(
                    ProviderException.class,
                    () -> ActiveChoicesChoiceResponse.options(
                            overflow.getBytes(StandardCharsets.UTF_8), true, "bytes"));
        });
    }
}
