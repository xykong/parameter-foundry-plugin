package io.jenkins.plugins.parameterfoundry.provider;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ActiveChoicesJsonResponseTest {
    private Map<String, String> parse(String body) {
        return ActiveChoicesJsonResponse.parse(body.getBytes(StandardCharsets.UTF_8), "synthetic");
    }

    @Test
    void listOrderAndDuplicateKeysMatchTheSource() {
        var values = parse("{\"data\":[\"gamma\",\"alpha\",\"gamma\"]}");
        assertEquals(List.of("gamma", "alpha"), List.copyOf(values.keySet()));
        assertEquals(Map.of("gamma", "gamma", "alpha", "alpha"), values);
        assertEquals(
                Map.of("gamma:selected", "gamma:selected", "alpha:disabled", "alpha:disabled"),
                parse("{\"data\":[\"gamma:selected\",\"alpha:disabled\"]}"));
    }

    @Test
    void mapLabelsAndOrderUseTheSameJsonParserAsTheSource() {
        var values = parse("{\"data\":{\"gamma\":\"Gamma label\",\"alpha\":\"Alpha label\"}}");
        assertEquals(List.of("alpha", "gamma"), List.copyOf(values.keySet()));
        assertEquals(Map.of("gamma", "Gamma label", "alpha", "Alpha label"), values);
        assertEquals(
                Map.of("code", "1", "message", "Synthetic error"),
                parse("{\"code\":1,\"message\":\"Synthetic error\"}"));
    }

    @Test
    void emptyAndNullDataSelectTheRootButEmptyRootDoesNotFail() {
        assertEquals(Map.of("code", "0", "data", "[]"), parse("{\"data\":[],\"code\":0}"));
        var values = parse("{\"data\":null,\"code\":0}");
        assertTrue(values.containsKey("data"));
        assertNull(values.get("data"));
        assertEquals("0", values.get("code"));
        assertTrue(parse("[]").isEmpty());
        assertEquals(Map.of("data", "1E-1000"), parse("{\"data\":1e-1000}"));
        assertEquals(Map.of("data", "-1E-1000"), parse("{\"data\":-1e-1000}"));
        assertEquals(Map.of("data", "0"), parse("{\"data\":0}"));
    }

    @Test
    void rootMapListsExposeDataAndRetainNullKeys() {
        var values = parse("[{\"data\":\"gamma\"},{\"data\":\"alpha\"}]");
        assertEquals(List.of("gamma", "alpha"), List.copyOf(values.keySet()));
        var missing = parse("[{},{}]");
        assertEquals(1, missing.size());
        assertTrue(missing.containsKey(null));
        assertNull(missing.get(null));
    }

    @Test
    void invalidJsonAndBareNonemptyScalarListsSignalConfiguredFallback() {
        assertThrows(ProviderException.class, () -> parse("invalid"));
        assertThrows(ProviderException.class, () -> parse("[\"gamma\",\"alpha\"]"));
        assertThrows(ProviderException.class, () -> parse("null"));
        assertTrue(parse("{\"data\":true}").isEmpty());
    }
}
