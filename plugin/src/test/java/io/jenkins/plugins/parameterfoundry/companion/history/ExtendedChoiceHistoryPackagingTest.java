package io.jenkins.plugins.parameterfoundry.companion.history;

import static org.junit.jupiter.api.Assertions.*;

import hudson.EnvVars;
import hudson.model.StringParameterValue;
import java.io.ByteArrayInputStream;
import java.io.ObjectStreamClass;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.TreeSet;
import java.util.jar.JarInputStream;
import org.junit.jupiter.api.Test;

class ExtendedChoiceHistoryPackagingTest {
    private static final String RESOURCE =
            "/io/jenkins/plugins/parameterfoundry/companion/history/legacy-extended-choice-values.jar";
    private static final String LEGACY =
            "com.cwctravel.hudson.plugins.extended_choice_parameter.ExtendedChoiceParameterValue";

    @Test
    void nestedJarIsDigestBoundAndContainsOnlyTheReviewedValueAndView() throws Exception {
        byte[] bytes;
        try (var input = getClass().getResourceAsStream(RESOURCE)) {
            assertNotNull(input);
            bytes = input.readAllBytes();
        }
        assertTrue(bytes.length < 32768);
        try (var input = getClass().getResourceAsStream(RESOURCE + ".sha256")) {
            assertNotNull(input);
            assertEquals(
                    new String(input.readAllBytes(), StandardCharsets.US_ASCII).trim(),
                    HexFormat.of()
                            .formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        }
        var entries = new TreeSet<String>();
        try (var jar = new JarInputStream(new ByteArrayInputStream(bytes))) {
            assertEquals(
                    "parameter-foundry", jar.getManifest().getMainAttributes().getValue("Short-Name"));
            assertNotNull(jar.getManifest().getMainAttributes().getValue("Plugin-Version"));
            for (var entry = jar.getNextJarEntry(); entry != null; entry = jar.getNextJarEntry()) {
                if (!entry.isDirectory()) entries.add(entry.getName());
            }
        }
        assertEquals(
                new TreeSet<>(List.of(LEGACY.replace('.', '/') + ".class", LEGACY.replace('.', '/') + "/value.jelly")),
                entries);
        assertFalse(Files.exists(Path.of("target/classes", LEGACY.replace('.', '/') + ".class")));
    }

    @Test
    void isolatedClassRetainsNativeUidAndLiteralBehaviorWithoutUsingTheTestFixture() throws Exception {
        URL resource = getClass().getResource(RESOURCE);
        assertNotNull(resource);
        // This source set must never be on the parent classpath. Bypass only the
        // test fixture with the same binary name when inspecting the nested jar.
        try (var loader = new URLClassLoader(new URL[] {resource}, getClass().getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (!LEGACY.equals(name)) return super.loadClass(name, resolve);
                Class<?> type = findLoadedClass(name);
                if (type == null) type = findClass(name);
                if (resolve) resolveClass(type);
                return type;
            }
        }) {
            Class<?> type = loader.loadClass(LEGACY);
            assertSame(loader, type.getClassLoader());
            assertEquals(StringParameterValue.class, type.getSuperclass());
            assertEquals(7993744779892775177L, ObjectStreamClass.lookup(type).getSerialVersionUID());
            assertEquals(1, type.getDeclaredFields().length);
            assertEquals(0, type.getDeclaredMethods().length);
            assertNotNull(loader.findResource(LEGACY.replace('.', '/') + "/value.jelly"));
            for (String literal : List.of("", " padded,ref ", "quote\"&<value>", "中文")) {
                var value = (StringParameterValue)
                        type.getConstructor(String.class, String.class).newInstance("REF", literal);
                value.setDescription("Historical description");
                assertEquals(literal, value.getValue());
                assertEquals("Historical description", value.getDescription());
                var env = new EnvVars();
                value.buildEnvironment(null, env);
                assertEquals(literal, env.get("REF"));
                assertEquals(literal, value.createVariableResolver(null).resolve("REF"));
            }
        }
    }
}
