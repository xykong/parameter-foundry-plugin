package io.jenkins.plugins.parameterfoundry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Keeps the migration and retirement surfaces translatable: every message key referenced by Java
 * or Jelly code must exist, and every locale must translate the full key set.
 */
class MigrationMessageCoverageTest {

    private static final Pattern MESSAGE_CALL = Pattern.compile("Messages\\.([A-Za-z0-9_]+)\\(");
    private static final Pattern MESSAGE_TEXT = Pattern.compile("MigrationText\\.of\\(\"([A-Za-z0-9_]+)\"");
    private static final Pattern VIEW_MESSAGE = Pattern.compile("\\$\\{%([A-Za-z0-9_]+)");
    private static final List<String> LOCALES = List.of("zh_CN", "ja", "ko");

    @Test
    void everyJavaReferenceResolvesAndEveryLocaleIsComplete() throws IOException {
        Path resources = Path.of("target", "classes", "io", "jenkins", "plugins", "parameterfoundry");
        Set<String> english = keys(resources.resolve("Messages.properties"));
        assertTrue(english.size() > 100, "Unexpected message count: " + english.size());
        for (String locale : LOCALES) {
            Path bundle = resources.resolve("Messages_" + locale + ".properties");
            assertTrue(Files.isRegularFile(bundle), bundle.toString());
            assertEquals(english, keys(bundle), locale + " must translate every message key");
        }

        Set<String> referenced = new TreeSet<>();
        try (Stream<Path> paths = Files.walk(Path.of("src", "main", "java"))) {
            for (Path source :
                    paths.filter(path -> path.toString().endsWith(".java")).toList()) {
                String text = Files.readString(source);
                collect(MESSAGE_CALL, text, referenced);
                collect(MESSAGE_TEXT, text, referenced);
            }
        }
        Set<String> missing = new TreeSet<>(referenced);
        missing.removeAll(english);
        assertTrue(missing.isEmpty(), "Message keys without a bundle entry: " + missing);
    }

    @Test
    void everyViewKeyExistsInTheViewBundleAndItsLocales() throws IOException {
        Path resources = Path.of("target", "classes", "io", "jenkins", "plugins", "parameterfoundry");
        List<Path> views;
        try (Stream<Path> paths = Files.walk(resources)) {
            views = paths.filter(path -> path.toString().endsWith(".jelly")).toList();
        }
        assertTrue(!views.isEmpty());
        for (Path view : views) {
            String jelly = Files.readString(view);
            Set<String> used = new TreeSet<>();
            collect(VIEW_MESSAGE, jelly, used);
            if (used.isEmpty()) {
                continue;
            }
            Path base = view.resolveSibling(stripSuffix(view.getFileName().toString()) + ".properties");
            assertTrue(Files.isRegularFile(base), "Missing view bundle for " + view + ": " + base);
            Set<String> english = keys(base);
            Set<String> unknown = new TreeSet<>(used);
            unknown.removeAll(english);
            assertTrue(unknown.isEmpty(), "Keys without a view bundle entry in " + base + ": " + unknown);
            for (String locale : LOCALES) {
                Path bundle =
                        view.resolveSibling(stripSuffix(view.getFileName().toString()) + "_" + locale + ".properties");
                assertTrue(Files.isRegularFile(bundle), "Missing view bundle: " + bundle);
                Set<String> localized = keys(bundle);
                Set<String> untranslated = new TreeSet<>(english);
                untranslated.removeAll(localized);
                assertTrue(untranslated.isEmpty(), "Untranslated view keys in " + bundle + ": " + untranslated);
            }
        }
    }

    @Test
    void apostrophesSurviveMessageFormat() {
        assertTrue(Messages.MigrationDecisionGitCombinedRefs().contains("source's shortened names"));
        assertTrue(Messages.MigrationErrorUnknownRecord("42").contains("42"));
        assertTrue(Messages.MigrationEvidencePass("1", "2").contains("#1"));
    }

    private static String stripSuffix(String fileName) {
        return fileName.substring(0, fileName.length() - ".jelly".length());
    }

    private static void collect(Pattern pattern, String text, Set<String> target) {
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            target.add(matcher.group(1));
        }
    }

    private static Set<String> keys(Path properties) throws IOException {
        Set<String> keys = new TreeSet<>();
        for (String line : Files.readAllLines(properties)) {
            String trimmed = line.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("#") || !trimmed.contains("=")) {
                continue;
            }
            keys.add(trimmed.substring(0, trimmed.indexOf('=')).strip());
        }
        return keys;
    }
}
