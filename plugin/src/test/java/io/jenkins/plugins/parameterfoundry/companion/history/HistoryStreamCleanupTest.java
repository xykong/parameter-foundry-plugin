package io.jenkins.plugins.parameterfoundry.companion.history;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class HistoryStreamCleanupTest {
    @Test
    void uncheckedCloseStillCleansAndPreservesPrimaryAndSecondaryFailures() {
        for (Throwable primary : new Throwable[] {
            new IOException("close"), new IllegalStateException("close"), new AssertionError("close")
        }) {
            var cleaned = new AtomicBoolean();
            var secondary = new IOException("cleanup");
            Throwable actual = assertThrows(
                    Throwable.class,
                    () -> HistoryStreamCleanup.close(() -> HistoryStreamCleanup.rethrow(primary), () -> {
                        cleaned.set(true);
                        throw secondary;
                    }));
            assertTrue(cleaned.get());
            assertSame(primary, actual);
            assertArrayEquals(new Throwable[] {secondary}, actual.getSuppressed());
        }
    }

    @Test
    void allCleanupOperationsRunWithoutSelfSuppressionOrMasking() throws Exception {
        var primary = new IOException("primary");
        var secondary = new AssertionError("secondary");
        assertSame(primary, HistoryStreamCleanup.attempt(primary, () -> {
            throw primary;
        }));
        assertEquals(0, primary.getSuppressed().length);
        assertSame(primary, HistoryStreamCleanup.attempt(primary, () -> {
            throw secondary;
        }));
        assertArrayEquals(new Throwable[] {secondary}, primary.getSuppressed());
        var cleaned = new AtomicBoolean();
        HistoryStreamCleanup.close(() -> {}, () -> cleaned.set(true));
        assertTrue(cleaned.get());
    }
}
