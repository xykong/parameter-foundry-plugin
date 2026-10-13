package io.jenkins.plugins.parameterfoundry.companion.history;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Set;
import java.util.jar.Manifest;
import java.util.zip.ZipInputStream;

/** Verifies and retains only the immutable, packaged history reader resource. */
final class HistoryReaderArchive {
    static final String RESOURCE = "io/jenkins/plugins/parameterfoundry/companion/history/legacy-git-values.jar";
    static final String LEGACY = "com.syhuang.hudson.plugins.listgitbranchesparameter.ListGitBranchesParameterValue";
    private static final int LIMIT = 32768;

    enum Family {
        GIT(RESOURCE, LEGACY),
        EXTENDED_CHOICE(
                "io/jenkins/plugins/parameterfoundry/companion/history/legacy-extended-choice-values.jar",
                "com.cwctravel.hudson.plugins.extended_choice_parameter.ExtendedChoiceParameterValue"),
        FILE(
                "io/jenkins/plugins/parameterfoundry/companion/history/legacy-file-values.jar",
                "io.jenkins.plugins.file_parameters.AbstractFileParameterValue",
                Set.of(
                        "META-INF/MANIFEST.MF",
                        "META-INF/services/annotations/org.kohsuke.stapler.export.ExportedBean",
                        "META-INF/services/annotations/org.jenkinsci.Symbol",
                        "io/jenkins/plugins/file_parameters/AbstractFileParameterValue.class",
                        "io/jenkins/plugins/file_parameters/Base64FileParameterValue.class",
                        "io/jenkins/plugins/file_parameters/StashedFileParameterValue.class",
                        "io/jenkins/plugins/file_parameters/FileParameterWrapper.class",
                        "io/jenkins/plugins/file_parameters/FileParameterWrapper$DescriptorImpl.class",
                        "io/jenkins/plugins/file_parameters/FileParameterWrapper.stapler",
                        "io/jenkins/plugins/file_parameters/AbstractFileParameterValue/value.jelly"));

        final String resource;
        final String legacy;
        final Set<String> entries;

        Family(String resource, String legacy) {
            this(
                    resource,
                    legacy,
                    Set.of(
                            "META-INF/MANIFEST.MF",
                            legacy.replace('.', '/') + ".class",
                            legacy.replace('.', '/') + "/value.jelly"));
        }

        Family(String resource, String legacy, Set<String> entries) {
            this.resource = resource;
            this.legacy = legacy;
            this.entries = entries;
        }
    }

    private HistoryReaderArchive() {}

    static byte[] packagedBytes() throws IOException {
        return packagedBytes(Family.GIT);
    }

    static byte[] packagedBytes(Family family) throws IOException {
        URL archive = ownedResource(family.resource);
        URL checksumResource = ownedResource(family.resource + ".sha256");
        try (var jar = archive.openStream();
                var checksum = checksumResource.openStream()) {
            byte[] bytes = jar.readNBytes(LIMIT + 1);
            byte[] hash = checksum.readNBytes(129);
            if (hash.length > 128) throw new IOException("History reader checksum is oversized");
            verify(bytes, new String(hash, StandardCharsets.US_ASCII).trim(), family);
            return bytes;
        }
    }

    private static URL ownedResource(String name) throws IOException {
        URL resource = HistoryReaderArchive.class.getResource("/" + name);
        URL origin =
                HistoryReaderArchive.class.getProtectionDomain().getCodeSource().getLocation();
        if (resource == null) throw new IOException("History reader resource is missing");
        if (!"file".equals(origin.getProtocol())) throw new IOException("History reader code origin is not local");
        if ("jar".equals(resource.getProtocol())) {
            var connection = (JarURLConnection) resource.openConnection();
            if (!connection.getJarFileURL().toExternalForm().equals(origin.toExternalForm())) {
                throw new IOException("History reader resource has a different code origin");
            }
        } else if (!resource.toExternalForm().equals(new URL(origin, name).toExternalForm())) {
            throw new IOException("History reader resource has a different code origin");
        }
        return resource;
    }

    static void verify(byte[] bytes, String expected) throws IOException {
        verify(bytes, expected, Family.GIT);
    }

    static void verify(byte[] bytes, String expected, Family family) throws IOException {
        if (bytes.length > LIMIT
                || !expected.matches("[0-9a-f]{64}")
                || !digest(bytes).equals(expected)) {
            throw new IOException("History reader integrity check failed");
        }
        var names = new HashSet<String>();
        int expanded = 0;
        try (var zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                byte[] content = zip.readNBytes(LIMIT + 1);
                expanded += content.length;
                if (expanded > LIMIT) throw new IOException("History reader expanded content is oversized");
                if (entry.isDirectory()) {
                    String directory = entry.getName();
                    if (content.length != 0 || family.entries.stream().noneMatch(name -> name.startsWith(directory))) {
                        throw new IOException("History reader contains an unexpected directory");
                    }
                    continue;
                }
                if (!family.entries.contains(entry.getName()) || !names.add(entry.getName())) {
                    throw new IOException("History reader contains an unexpected entry");
                }
                if ("META-INF/MANIFEST.MF".equals(entry.getName())) {
                    var manifest = new Manifest(new ByteArrayInputStream(content));
                    var attributes = manifest.getMainAttributes();
                    if (!"parameter-foundry".equals(attributes.getValue("Short-Name"))
                            || attributes.getValue("Plugin-Version") == null) {
                        throw new IOException("History reader manifest ownership is invalid");
                    }
                    if (family == Family.FILE
                            && (!manifest.getEntries().isEmpty()
                                    || !attributes.keySet().stream()
                                            .map(Object::toString)
                                            .collect(java.util.stream.Collectors.toSet())
                                            .equals(Set.of(
                                                    "Manifest-Version",
                                                    "Short-Name",
                                                    "Plugin-Version",
                                                    "Ant-Version",
                                                    "Created-By"))
                                    || !"1.0".equals(attributes.getValue("Manifest-Version"))
                                    || !"Parameter Foundry".equals(attributes.getValue("Created-By")))) {
                        throw new IOException("File history manifest contains unsupported loading declarations");
                    }
                }
                if (family == Family.FILE && entry.getName().startsWith("META-INF/services/annotations/")) {
                    var lines =
                            new String(content, StandardCharsets.UTF_8).lines().toList();
                    Set<String> expectedIndex = entry.getName().endsWith("org.jenkinsci.Symbol")
                            ? Set.of("io.jenkins.plugins.file_parameters.FileParameterWrapper$DescriptorImpl")
                            : Set.of(
                                    "io.jenkins.plugins.file_parameters.AbstractFileParameterValue",
                                    "io.jenkins.plugins.file_parameters.Base64FileParameterValue",
                                    "io.jenkins.plugins.file_parameters.StashedFileParameterValue");
                    if (lines.size() != expectedIndex.size() || !new HashSet<>(lines).equals(expectedIndex)) {
                        throw new IOException("File history annotation index is invalid");
                    }
                }
            }
        }
        if (!names.equals(family.entries)) throw new IOException("History reader content is incomplete");
    }

    static Path retain(Path managedRoot, byte[] bytes) throws IOException {
        return retain(managedRoot, bytes, Family.GIT);
    }

    static Path retain(Path managedRoot, byte[] bytes, Family family) throws IOException {
        String hash = digest(bytes);
        verify(bytes, hash, family);
        ensureManagedRoot(managedRoot);
        Path target = managedRoot.resolve(hash + ".jar");
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            verifyRetained(target, bytes);
            return target;
        }
        Path temporary = Files.createTempFile(managedRoot, "reader-", ".tmp");
        try {
            Files.write(temporary, bytes);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException unsupported) {
                throw new IOException("History reader storage does not support atomic publication", unsupported);
            }
            verifyRetained(target, bytes);
            return target;
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void verifyRetained(Path target, byte[] bytes) throws IOException {
        if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS) || Files.size(target) != bytes.length) {
            throw new IOException("History reader retained archive is invalid");
        }
        try (var stream = Files.newInputStream(target, LinkOption.NOFOLLOW_LINKS)) {
            if (!MessageDigest.isEqual(bytes, stream.readNBytes(LIMIT + 1))) {
                throw new IOException("History reader retained archive differs from the packaged resource");
            }
        }
    }

    static void ensureManagedRoot(Path root) throws IOException {
        try {
            Files.createDirectory(root);
        } catch (FileAlreadyExistsException existing) {
            // Another family can create the same fixed managed directory concurrently.
        }
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("History reader storage is not a regular directory");
        }
    }

    static String digest(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
