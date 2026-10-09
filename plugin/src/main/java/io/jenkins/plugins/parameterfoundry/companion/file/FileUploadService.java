package io.jenkins.plugins.parameterfoundry.companion.file;

import hudson.model.Failure;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Base64;

/** Shared bounded decoding and streaming for UI, multipart, CLI and Pipeline values. */
public final class FileUploadService {
    public static final long MAX_BASE64_BYTES = 1024 * 1024;
    public static final long MAX_STASHED_BYTES = 100 * 1024 * 1024;

    private FileUploadService() {}

    static void checkName(String name) {
        if (name == null || !name.matches("[A-Za-z_][A-Za-z0-9_.-]{0,99}")) {
            throw new Failure(
                    "File parameter names must start with a letter or underscore and contain at most 100 letters, digits, underscores, dots or hyphens.");
        }
    }

    static String encode(InputStream src) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        copy(src, bytes, MAX_BASE64_BYTES);
        return Base64.getEncoder().encodeToString(bytes.toByteArray());
    }

    static byte[] decode(String value) throws IOException {
        if (value == null || value.length() > 4 * ((MAX_BASE64_BYTES + 2) / 3)) {
            throw new IOException("Base64 file exceeds the 1 MiB limit or is missing.");
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(value);
            if (bytes.length > MAX_BASE64_BYTES) {
                throw new IOException("Base64 file exceeds the 1 MiB limit.");
            }
            return bytes;
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid Base64 file content.", e);
        }
    }

    static void copy(InputStream src, OutputStream dst, long limit) throws IOException {
        byte[] buffer = new byte[8192];
        long count = 0;
        int read;
        while ((read = src.read(buffer)) != -1) {
            count += read;
            if (count > limit) {
                throw new IOException("File upload exceeds the " + limit + " byte limit.");
            }
            dst.write(buffer, 0, read);
        }
    }
}
