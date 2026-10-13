package io.jenkins.plugins.parameterfoundry.companion.history;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Random;
import org.junit.jupiter.api.Test;

class HistoricalFileStreamsTest {
    @Test
    void nativeDecoderGrammarAndBinaryContentArePreserved() throws Exception {
        var random = new Random(8204);
        for (int size = 0; size < 2048; size++) {
            byte[] data = new byte[size];
            random.nextBytes(data);
            for (String encoded : new String[] {
                Base64.getEncoder().encodeToString(data),
                Base64.getEncoder().withoutPadding().encodeToString(data)
            }) {
                try (var stream = HistoricalFileStreams.base64(encoded, size)) {
                    assertArrayEquals(Base64.getDecoder().decode(encoded), stream.readAllBytes());
                }
            }
        }
        for (String malformed : new String[] {"A", "AA=", "A===", "AAAA=", "AA==A", "AA\n==", "éA==", "__=="}) {
            assertThrows(
                    IllegalArgumentException.class, () -> Base64.getDecoder().decode(malformed));
            assertThrows(IOException.class, () -> HistoricalFileStreams.base64(malformed, 100));
        }
        assertThrows(IOException.class, () -> HistoricalFileStreams.base64(null, 10));
    }

    @Test
    void exactLimitRequiresEofAndOverflowCannotBeBypassed() throws Exception {
        try (var exact = HistoricalFileStreams.limit(new ByteArrayInputStream(new byte[5]), 5)) {
            assertEquals(5, exact.readAllBytes().length);
        }
        try (var over = HistoricalFileStreams.limit(new ByteArrayInputStream(new byte[6]), 5)) {
            assertEquals(4, over.skip(4));
            assertEquals(0, over.read());
            assertThrows(IOException.class, over::read);
            assertThrows(IOException.class, () -> over.skip(1));
            assertThrows(IOException.class, () -> over.read(new byte[3]));
            assertFalse(over.markSupported());
            assertThrows(IOException.class, over::reset);
        }
        try (var skip = HistoricalFileStreams.limit(new ByteArrayInputStream(new byte[6]), 5)) {
            assertThrows(IOException.class, () -> skip.skip(Long.MAX_VALUE));
            assertThrows(IOException.class, skip::read);
        }
        try (var empty = HistoricalFileStreams.base64("", 0)) {
            assertEquals(-1, empty.read());
        }
        try (var over = HistoricalFileStreams.base64("AA==", 0)) {
            assertThrows(IOException.class, over::read);
        }
        try (var huge = HistoricalFileStreams.limit(new ByteArrayInputStream(new byte[] {42}), Long.MAX_VALUE)) {
            assertEquals(42, huge.read());
            assertEquals(-1, huge.read());
        }
    }

    @Test
    void closeReachesSourceAfterBudgetFailure() throws Exception {
        class Source extends ByteArrayInputStream {
            boolean closed;

            Source() {
                super(new byte[2]);
            }

            @Override
            public void close() {
                closed = true;
            }
        }
        var source = new Source();
        try (var stream = HistoricalFileStreams.limit(source, 1)) {
            assertThrows(IOException.class, stream::readAllBytes);
        }
        assertTrue(source.closed);
    }
}
