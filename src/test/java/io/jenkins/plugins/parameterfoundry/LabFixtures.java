package io.jenkins.plugins.parameterfoundry;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Resolves the fixture values a test may not hard-code.
 *
 * <p>The public repository ships synthetic defaults. A maintainer run sets
 * {@code PF_LAB_FIXTURES} to a properties file with the same keys holding the
 * real values, so the same tests exercise a real environment without those
 * values ever entering the repository.
 *
 * <p>See {@code docs/decisions/0018-public-hygiene-and-private-lab-overlay.md}.
 */
public final class LabFixtures {

    /** Environment variable pointing at the overriding properties file. */
    public static final String OVERRIDE_ENV = "PF_LAB_FIXTURES";

    private static final String DEFAULTS_RESOURCE = "/lab-fixtures.properties";
    private static final Properties VALUES = load();

    private LabFixtures() {}

    /** Returns the value for the key, failing loudly when a test asks for a key nobody defined. */
    public static String get(String key) {
        String value = VALUES.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Unknown lab fixture: " + key);
        }
        return value;
    }

    /** Convenience for the common {@code value@column} cell format. */
    public static String cell(String rowKey, String columnKey) {
        return get(rowKey) + "@" + get(columnKey);
    }

    private static Properties load() {
        Properties properties = new Properties();
        try (InputStream stream = LabFixtures.class.getResourceAsStream(DEFAULTS_RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("Missing " + DEFAULTS_RESOURCE + " on the test classpath");
            }
            properties.load(stream);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        String override = System.getenv(OVERRIDE_ENV);
        if (override != null && !override.isBlank()) {
            Path path = Path.of(override);
            if (!Files.isReadable(path)) {
                throw new IllegalStateException(OVERRIDE_ENV + " is not readable: " + path);
            }
            try (InputStream stream = Files.newInputStream(path)) {
                properties.load(stream);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return properties;
    }
}
