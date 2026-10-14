package io.jenkins.plugins.parameterfoundry.provider;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class RawScalarChoiceAdapterTest {
    @Test
    void preservesWholeResponseApiValuesAndDeclaredOrder() {
        for (String body : List.of(
                "topic/test",
                "topic/test\n",
                "line1\nline2",
                "  topic/test  ",
                "{\"data\":\"topic/test\"}",
                "\"topic/test\"",
                "",
                "null\n")) {
            var response = RawScalarChoiceAdapter.interpret(
                    body.getBytes(StandardCharsets.UTF_8), List.of("null"), List.of("topic/stable"));
            assertEquals(2, response.choices().size(), body);
            assertEquals(body, response.choices().get(0).apiValue(), body);
            assertEquals(body, response.apiDefault(), body);
            assertEquals("topic/stable", response.choices().get(1).apiValue(), body);
        }
    }

    @Test
    void exactSentinelsAndDuplicateTailKeepSourceMapSemantics() {
        var ignored = RawScalarChoiceAdapter.interpret(
                "null".getBytes(StandardCharsets.UTF_8), List.of("null"), List.of("topic/stable"));
        assertEquals(1, ignored.choices().size());
        assertEquals("topic/stable", ignored.apiDefault());
        var duplicate = RawScalarChoiceAdapter.interpret(
                "topic/stable".getBytes(StandardCharsets.UTF_8), List.of(), List.of("topic/stable", "topic/stable"));
        assertEquals(1, duplicate.choices().size());
        var empty = RawScalarChoiceAdapter.interpret(new byte[0], List.of(""), List.of());
        assertTrue(empty.choices().isEmpty());
        assertEquals("", empty.apiDefault());
        assertEquals(-1, empty.selectedIndex());
    }

    @Test
    void utf8ReplacementAndSourceMarkersUseExistingInterpreter() {
        var malformed = RawScalarChoiceAdapter.interpret(new byte[] {(byte) 0xe9}, List.of(), List.of());
        assertEquals("\ufffd", malformed.apiDefault());
        var selected = RawScalarChoiceAdapter.interpret(
                "topic/test:selected".getBytes(StandardCharsets.UTF_8), List.of(), List.of("topic/stable"));
        assertEquals("topic/test", selected.apiDefault());
        assertTrue(selected.choices().get(0).sourceSelected());
        assertEquals(0, selected.selectedIndex());
        var disabled = RawScalarChoiceAdapter.interpret(
                "topic/test:disabled".getBytes(StandardCharsets.UTF_8), List.of(), List.of("topic/stable"));
        assertTrue(disabled.choices().get(0).disabled());
        assertEquals(1, disabled.selectedIndex());
        assertEquals("topic/test:disabled", disabled.apiDefault());
    }
}
