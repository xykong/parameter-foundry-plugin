package io.jenkins.plugins.unifiedparameters.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class OptionImportServiceTest {

    @Test
    void importsJsonAndYamlWithoutExecutableTypes() {
        List<Option> json = OptionImportService.parse("""
                [
                  {"value":"android","label":"Android","group":"Mobile"},
                  "web"
                ]
                """);
        List<Option> yaml = OptionImportService.parse("""
                options:
                  - value: ios
                    label: iOS
                    tags: [mobile, release]
                  - value: web
                """);

        assertEquals(
                List.of("android", "web"), json.stream().map(Option::getValue).toList());
        assertEquals("Mobile", json.get(0).getGroup());
        assertEquals(List.of("mobile", "release"), yaml.get(0).getTags());
    }

    @Test
    void rejectsAliasesAndInvalidRoots() {
        assertThrows(IllegalArgumentException.class, () -> OptionImportService.parse("""
                        common: &common
                          value: android
                        options:
                          - *common
                        """));
        assertThrows(IllegalArgumentException.class, () -> OptionImportService.parse("value: android"));
    }

    @Test
    void rejectsDuplicateValuesAndRedactsParserInternals() {
        IllegalArgumentException duplicate = assertThrows(
                IllegalArgumentException.class,
                () -> OptionImportService.parse("- value: repeated\n- value: repeated\n"));
        assertTrue(duplicate.getMessage().contains("duplicate option value"));

        IllegalArgumentException malformed =
                assertThrows(IllegalArgumentException.class, () -> OptionImportService.parse("[not-json"));
        assertEquals("Import content is not valid YAML or JSON.", malformed.getMessage());
    }
}
