package io.jenkins.plugins.parameterfoundry.companion.history;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.Objects;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/** Streaming primitives for stored file values; callers own authorization and the source lifetime. */
@Restricted(NoExternalUse.class)
public final class HistoricalFileStreams {
    private HistoricalFileStreams() {}

    /** Validate the stored encoding without allocating a second copy, then decode under a byte budget. */
    public static InputStream base64(String value, long maximum) throws IOException {
        if (maximum < 0) throw new IllegalArgumentException("Negative history read budget");
        if (value == null) throw new IOException("Historical Base64 content is missing");
        int padding = 0;
        int data = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '=') {
                if (++padding > 2) throw new IOException("Invalid historical Base64 padding");
            } else {
                if (padding != 0
                        || !(c >= 'A' && c <= 'Z'
                                || c >= 'a' && c <= 'z'
                                || c >= '0' && c <= '9'
                                || c == '+'
                                || c == '/')) {
                    throw new IOException("Invalid historical Base64 data");
                }
                data++;
            }
        }
        if (padding == 0
                ? data % 4 == 1
                : value.length() % 4 != 0 || padding == 1 && data % 4 != 3 || padding == 2 && data % 4 != 2) {
            throw new IOException("Invalid historical Base64 ending");
        }
        return limit(
                Base64.getDecoder().wrap(new InputStream() {
                    private int position;

                    @Override
                    public int read() {
                        return position == value.length() ? -1 : value.charAt(position++);
                    }
                }),
                maximum);
    }

    /** Reject the first byte beyond the limit, including skips; failures remain sticky. */
    public static InputStream limit(InputStream input, long maximum) {
        Objects.requireNonNull(input);
        if (maximum < 0) throw new IllegalArgumentException("Negative history read budget");
        return new FilterInputStream(input) {
            private long count;
            private boolean exceeded;

            private void requireAvailable() throws IOException {
                if (exceeded) throw new IOException("Historical file read budget exceeded");
            }

            private long allowance(long requested) {
                long remaining = maximum - count;
                return Math.min(requested, remaining == Long.MAX_VALUE ? Long.MAX_VALUE : remaining + 1);
            }

            private void account(long read) throws IOException {
                if (read > 0) {
                    if (read > maximum - count) {
                        exceeded = true;
                        throw new IOException("Historical file read budget exceeded");
                    }
                    count += read;
                }
            }

            @Override
            public int read() throws IOException {
                requireAvailable();
                int value = in.read();
                if (value >= 0) account(1);
                return value;
            }

            @Override
            public int read(byte[] buffer, int offset, int length) throws IOException {
                Objects.checkFromIndexSize(offset, length, buffer.length);
                if (length == 0) return 0;
                requireAvailable();
                int read = in.read(buffer, offset, (int) allowance(length));
                account(read);
                return read;
            }

            @Override
            public long skip(long requested) throws IOException {
                if (requested <= 0) return 0;
                requireAvailable();
                long skipped = in.skip(allowance(requested));
                account(skipped);
                return skipped;
            }

            @Override
            public boolean markSupported() {
                return false;
            }

            @Override
            public void mark(int readlimit) {}

            @Override
            public void reset() throws IOException {
                throw new IOException("Historical budget stream does not support reset");
            }
        };
    }
}
