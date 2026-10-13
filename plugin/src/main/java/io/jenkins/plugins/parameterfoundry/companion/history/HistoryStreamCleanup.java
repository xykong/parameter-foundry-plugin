package io.jenkins.plugins.parameterfoundry.companion.history;

import java.io.Closeable;
import java.io.IOException;

/** Preserve the primary failure while still attempting each owned cleanup operation. */
final class HistoryStreamCleanup {
    private HistoryStreamCleanup() {}

    @FunctionalInterface
    interface Operation {
        void run() throws IOException;
    }

    static Throwable attempt(Throwable primary, Operation operation) {
        try {
            operation.run();
        } catch (IOException | RuntimeException | Error failure) {
            if (primary == null) return failure;
            if (primary != failure) primary.addSuppressed(failure);
        }
        return primary;
    }

    static void rethrow(Throwable failure) throws IOException {
        if (failure instanceof IOException io) throw io;
        if (failure instanceof RuntimeException runtime) throw runtime;
        if (failure instanceof Error error) throw error;
        if (failure != null) throw new AssertionError("Unsupported cleanup failure", failure);
    }

    static void close(Closeable source, Operation cleanup) throws IOException {
        Throwable failure = attempt(null, source::close);
        rethrow(attempt(failure, cleanup));
    }
}
