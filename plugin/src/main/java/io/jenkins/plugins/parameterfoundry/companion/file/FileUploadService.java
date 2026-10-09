package io.jenkins.plugins.parameterfoundry.companion.file;

import hudson.model.Failure;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Base64;

/** Shared bounded decoding and streaming for UI, multipart, CLI and Pipeline values. */
public final class FileUploadService {
    // Legacy constant names retained for compatibility; these are transport defaults, not hard caps.
    public static final long MAX_BASE64_BYTES = 1024 * 1024;
    public static final long MAX_STASHED_BYTES = 100 * 1024 * 1024;

    private FileUploadService() {}

    static void checkName(String name) {
        if (name == null || !name.matches("[A-Za-z_][A-Za-z0-9_.-]{0,99}")) {
            throw new Failure(
                    "File parameter names must start with a letter or underscore and contain at most 100 letters, digits, underscores, dots or hyphens.");
        }
    }

    static void checkParameterName(String name) {
        if (name != null && name.matches("[A-Za-z_][A-Za-z0-9_.-]{0,99}")) {
            return;
        }
        checkWorkspacePath(name);
    }

    static String checkWorkspacePath(String path) {
        if (path == null || path.isBlank() || path.length() > 1024) {
            throw new Failure("A workspace-relative file path is required (at most 1024 characters).");
        }
        String normalized = path.replace('\\', '/');
        if (normalized.startsWith("/")
                || normalized.contains(":")
                || normalized.chars().anyMatch(c -> c < 32 || c == 127)) {
            throw new Failure(
                    "Workspace file paths must be relative and contain no drive, stream or control characters.");
        }
        for (String part : normalized.split("/", -1)) {
            if (part.isEmpty()
                    || part.equals(".")
                    || part.equals("..")
                    || part.endsWith(".")
                    || part.endsWith(" ")
                    || part.matches("(?i)(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(?:\\..*)?")) {
                throw new Failure("Workspace file paths cannot contain empty, dot or parent segments.");
            }
        }
        return normalized;
    }

    static long checkLimit(long limit, boolean base64) {
        long maximum = base64 ? (Integer.MAX_VALUE / 4L) * 3 : Long.MAX_VALUE;
        if (limit < 1 || limit > maximum) {
            throw new Failure("Upload limit must be positive and within the selected transport's representable range.");
        }
        return limit;
    }

    static long limitFor(hudson.model.Run<?, ?> build, AbstractFoundryFileParameterValue value, long fallback)
            throws IOException {
        if (build == null) {
            return fallback;
        }
        var property = build.getParent().getProperty(hudson.model.ParametersDefinitionProperty.class);
        var definition = property == null ? null : property.getParameterDefinition(value.getName());
        if (!(definition instanceof AbstractFoundryFileParameterDefinition file)
                || file.valueType() != value.getClass()) {
            throw new IOException("File value does not match the Job's current file definition.");
        }
        return file.getMaxUploadBytes();
    }

    static String encode(InputStream src) throws IOException {
        return encode(src, MAX_BASE64_BYTES);
    }

    static String encode(InputStream src, long limit) throws IOException {
        checkLimit(limit, true);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        copy(src, bytes, limit);
        return Base64.getEncoder().encodeToString(bytes.toByteArray());
    }

    static byte[] decode(String value) throws IOException {
        return decode(value, MAX_BASE64_BYTES);
    }

    static byte[] decode(String value, long limit) throws IOException {
        checkLimit(limit, true);
        if (value == null || value.length() > 4 * ((limit + 2) / 3)) {
            throw new IOException("Base64 file exceeds its configured decoded-byte limit or is missing.");
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(value);
            if (bytes.length > limit) {
                throw new IOException("Base64 file exceeds its configured decoded-byte limit.");
            }
            return bytes;
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid Base64 file content.", e);
        }
    }

    static void copy(InputStream src, OutputStream dst, long limit) throws IOException {
        checkLimit(limit, false);
        byte[] buffer = new byte[8192];
        long count = 0;
        int read;
        while ((read = src.read(buffer)) != -1) {
            if (read > limit - count) {
                throw new IOException("File upload exceeds the " + limit + " byte limit.");
            }
            count += read;
            dst.write(buffer, 0, read);
        }
    }
}
