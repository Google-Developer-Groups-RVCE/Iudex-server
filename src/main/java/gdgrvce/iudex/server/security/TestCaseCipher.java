package gdgrvce.iudex.server.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * Encrypts the test data that travels between the server and the judging client.
 *
 * <p>Test inputs go out encrypted and the client's captured outputs come back
 * encrypted, both under one AES-256-GCM key shared with the client.</p>
 *
 * <p><strong>Threat model.</strong> The client must decrypt the inputs to run
 * them, so it holds the key. This stops test data being read straight off the
 * API with a plain HTTP call, and stops it being harvested from the wire by
 * someone else on the network -- which matters, because a correct client's
 * captured outputs are the answer key. It does not stop a contestant who
 * extracts the key from the client. The defence that does not depend on the
 * client is that expected outputs never leave the server: the client sends what
 * its program printed, and {@code SubmissionService} decides what passed.</p>
 */
@Service
public class TestCaseCipher {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String ALGORITHM = "AES";
    private static final int KEY_BYTES = 32;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public TestCaseCipher(@Value("${iudex.testcase.secret:}") String secret) {
        // AES takes an exact key length, unlike HMAC, so a longer secret is cut
        // to the first 32 bytes rather than rejected. A secret generated the
        // documented way is already exactly that long.
        byte[] resolved = SecretKeys.resolve(secret, KEY_BYTES, "iudex.testcase.secret");
        this.key = new SecretKeySpec(Arrays.copyOf(resolved, KEY_BYTES), ALGORITHM);
    }

    /**
     * Returns base64 of {@code iv || ciphertext || tag}. A fresh IV per call
     * keeps identical inputs from producing identical ciphertext, so a
     * contestant cannot tell which test cases repeat.
     */
    public String encrypt(String plaintext) {
        byte[] iv = new byte[IV_BYTES];
        random.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] payload = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(ciphertext, 0, payload, iv.length, ciphertext.length);
            return Base64.getEncoder().encodeToString(payload);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to encrypt test data", exception);
        }
    }

    /**
     * Reverses {@link #encrypt}.
     *
     * @throws IllegalArgumentException if the payload is malformed or its
     *         authentication tag does not check out, which is a bad request
     *         rather than a server fault
     */
    public String decrypt(String encoded) {
        if (encoded == null) {
            throw new IllegalArgumentException("encrypted payload must not be null");
        }

        byte[] payload;
        try {
            payload = Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("encrypted payload must be base64-encoded", exception);
        }
        if (payload.length <= IV_BYTES) {
            throw new IllegalArgumentException("encrypted payload is too short to be valid");
        }

        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key,
                    new GCMParameterSpec(TAG_BITS, payload, 0, IV_BYTES));
            byte[] plaintext = cipher.doFinal(payload, IV_BYTES, payload.length - IV_BYTES);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException exception) {
            throw new IllegalArgumentException("encrypted payload could not be decrypted", exception);
        }
    }
}
