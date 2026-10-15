package io.jenkins.plugins.parameterfoundry.provider;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLPeerUnverifiedException;
import org.junit.jupiter.api.Test;

class TlsRejectionFingerprintTest {
    @Test
    void strictClassAndEncodingAreBoundedWithoutReadingSubclassMessages() throws Exception {
        for (String text : List.of("", "owned", "中文", "a\0b", "a".repeat(2048), "中".repeat(2048), "😀".repeat(1024))) {
            for (boolean handshake : List.of(true, false)) {
                SSLException failure =
                        handshake ? new SSLHandshakeException(text) : new SSLPeerUnverifiedException(text);
                String expected = java.util.HexFormat.of()
                        .formatHex(MessageDigest.getInstance("SHA-256")
                                .digest(("tls-rejected-v1\0"
                                                + failure.getClass().getName() + "\0" + text)
                                        .getBytes(StandardCharsets.UTF_8)));
                assertEquals(expected, TlsRejectionFingerprint.fingerprint(failure));
            }
        }
        assertNull(TlsRejectionFingerprint.fingerprint(null));
        assertNull(TlsRejectionFingerprint.fingerprint(new SSLHandshakeException(null)));
        for (String text : List.of("a".repeat(2049), "😀".repeat(1025), "\ud800", "\udc00")) {
            assertNull(TlsRejectionFingerprint.fingerprint(new SSLHandshakeException(text)));
        }
        assertNull(TlsRejectionFingerprint.fingerprint(new SSLException("owned")));
        assertNull(TlsRejectionFingerprint.fingerprint(new RuntimeException("owned")));
        assertNull(TlsRejectionFingerprint.fingerprint(new SSLHandshakeException("owned") {
            @Override
            public String getMessage() {
                throw new AssertionError("Subclass diagnostic was read");
            }
        }));
        assertNotNull(TlsRejectionFingerprint.fingerprint(new SSLHandshakeException("\ufffd")));
    }
}
