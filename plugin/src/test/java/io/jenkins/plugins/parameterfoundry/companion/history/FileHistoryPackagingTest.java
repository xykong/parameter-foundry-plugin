package io.jenkins.plugins.parameterfoundry.companion.history;

import static org.junit.jupiter.api.Assertions.*;

import hudson.EnvVars;
import hudson.model.ParameterValue;
import java.io.ObjectStreamClass;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FileHistoryPackagingTest {
    @Test
    void fileFamilyIsVerifiedRetainedAndAbsentFromMainClasses() throws Exception {
        byte[] file = HistoryReaderArchive.packagedBytes(HistoryReaderArchive.Family.FILE);
        assertThrows(
                java.io.IOException.class,
                () -> HistoryReaderArchive.verify(
                        file, HistoryReaderArchive.digest(file), HistoryReaderArchive.Family.GIT));
        for (String type : new String[] {
            "AbstractFileParameterValue",
            "Base64FileParameterValue",
            "StashedFileParameterValue",
            "FileParameterWrapper"
        }) {
            assertFalse(Files.exists(Path.of("target/classes/io/jenkins/plugins/file_parameters/" + type + ".class")));
        }
    }

    @Test
    void nativeSerializedFieldsUidAndLiteralEnvironmentArePreserved() throws Exception {
        var resource = getClass().getResource("/" + HistoryReaderArchive.Family.FILE.resource);
        assertNotNull(resource);
        try (var loader =
                new URLClassLoader(new java.net.URL[] {resource}, getClass().getClassLoader())) {
            var types = Map.of(
                    "AbstractFileParameterValue",
                    -3637270082524081687L,
                    "Base64FileParameterValue",
                    -5447497467668302565L,
                    "StashedFileParameterValue",
                    1L);
            for (var entry : types.entrySet()) {
                Class<?> type = loader.loadClass("io.jenkins.plugins.file_parameters." + entry.getKey());
                assertSame(loader, type.getClassLoader());
                assertEquals(
                        entry.getValue().longValue(),
                        ObjectStreamClass.lookup(type).getSerialVersionUID());
            }
            Class<?> base = loader.loadClass("io.jenkins.plugins.file_parameters.AbstractFileParameterValue");
            assertEquals(ParameterValue.class, base.getSuperclass());
            var filename = base.getDeclaredField("filename");
            filename.setAccessible(true);
            Class<?> type = loader.loadClass("io.jenkins.plugins.file_parameters.Base64FileParameterValue");
            ParameterValue value =
                    (ParameterValue) type.getConstructor(String.class).newInstance("UPLOAD");
            var encoded = type.getDeclaredField("base64");
            encoded.setAccessible(true);
            encoded.set(value, "AAECA/8ACg0gQUIAgA==");
            filename.set(value, "filename<&>.bin");
            var environment = new EnvVars();
            value.buildEnvironment(null, environment);
            assertEquals("AAECA/8ACg0gQUIAgA==", environment.get("UPLOAD"));
            assertEquals("filename<&>.bin", environment.get("UPLOAD_FILENAME"));
            assertEquals("AAECA/8ACg0gQUIAgA==", value.getValue());
            assertNotNull(
                    loader.findResource("io/jenkins/plugins/file_parameters/AbstractFileParameterValue/value.jelly"));
            assertEquals(
                    String.class,
                    loader.loadClass("io.jenkins.plugins.file_parameters.StashedFileParameterValue")
                            .getDeclaredField("tmpFile")
                            .getType());
        }
    }
}
