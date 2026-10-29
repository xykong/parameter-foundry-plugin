package io.jenkins.plugins.parameterfoundry.provider;

import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLPeerUnverifiedException;

/** Finite administrator-declared compatibility keys; never a TLS failure-reason classifier. */
final class TlsRejectionFingerprint {
    private TlsRejectionFingerprint() {}

    static String fingerprint(Throwable rejection) {
        if (rejection == null
                || rejection.getClass() != SSLHandshakeException.class
                        && rejection.getClass() != SSLPeerUnverifiedException.class) {
            return null;
        }
        String message = rejection.getMessage();
        // The limit is UTF-16 code units, not code points or encoded bytes.
        if (message == null || message.length() > 2048) return null;
        try {
            var encoded = StandardCharsets.UTF_8
                    .newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .encode(CharBuffer.wrap(message));
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(
                    ("tls-rejected-v1\0" + rejection.getClass().getName() + "\0").getBytes(StandardCharsets.UTF_8));
            digest.update(encoded);
            return HexFormat.of().formatHex(digest.digest());
        } catch (CharacterCodingException invalidUnicode) {
            return null;
        } catch (NoSuchAlgorithmException missingRequiredDigest) {
            throw new IllegalStateException("Required TLS fingerprint digest is unavailable.");
        }
    }
}
