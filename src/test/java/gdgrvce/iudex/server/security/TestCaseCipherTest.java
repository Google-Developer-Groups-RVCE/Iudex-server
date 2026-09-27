package gdgrvce.iudex.server.security;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Checks the cipher that guards test data on its way to and from the client. */
class TestCaseCipherTest {

    private static final String SECRET = "aXVkZXgtdGVzdC1rZXktMzItYnl0ZXMtZXhhY3RseSE=";

    private final TestCaseCipher cipher = new TestCaseCipher(SECRET);

    @Test
    void roundTripsThePlaintext() {
        String plaintext = "5\n1 2 3 4 5\n";
        assertEquals(plaintext, cipher.decrypt(cipher.encrypt(plaintext)));
    }

    @Test
    void roundTripsUnicodeAndEmptyInput() {
        assertEquals("", cipher.decrypt(cipher.encrypt("")));
        assertEquals("héllo → 世界", cipher.decrypt(cipher.encrypt("héllo → 世界")));
    }

    @Test
    void ciphertextDoesNotContainThePlaintext() {
        String encrypted = cipher.encrypt("the-answer");
        assertFalse(encrypted.contains("the-answer"));
        assertFalse(new String(Base64.getDecoder().decode(encrypted)).contains("the-answer"));
    }

    @Test
    void encryptingTheSameInputTwiceGivesDifferentCiphertext() {
        // A fresh IV each time, so a contestant cannot spot repeated test cases.
        assertNotEquals(cipher.encrypt("same input"), cipher.encrypt("same input"));
    }

    @Test
    void rejectsAPayloadEncryptedUnderADifferentKey() {
        TestCaseCipher other = new TestCaseCipher("b3RoZXIta2V5LXRoYXQtaXMtMzItYnl0ZXMtbG9uZyE=");
        String encrypted = other.encrypt("secret");

        assertThrows(IllegalArgumentException.class, () -> cipher.decrypt(encrypted));
    }

    @Test
    void rejectsATamperedPayload() {
        char[] encrypted = cipher.encrypt("some output").toCharArray();
        encrypted[encrypted.length - 2] = encrypted[encrypted.length - 2] == 'A' ? 'B' : 'A';

        assertThrows(IllegalArgumentException.class, () -> cipher.decrypt(new String(encrypted)));
    }

    @Test
    void rejectsMalformedPayloads() {
        assertThrows(IllegalArgumentException.class, () -> cipher.decrypt(null));
        assertThrows(IllegalArgumentException.class, () -> cipher.decrypt("not base64!!"));
        assertThrows(IllegalArgumentException.class, () -> cipher.decrypt("c2hvcnQ="));
    }

    @Test
    void rejectsAConfiguredSecretThatIsTooShort() {
        assertThrows(IllegalStateException.class, () -> new TestCaseCipher("c2hvcnQ="));
    }

    @Test
    void rejectsAConfiguredSecretThatIsNotBase64() {
        assertThrows(IllegalStateException.class, () -> new TestCaseCipher("not base64!!"));
    }

    @Test
    void generatesAWorkingKeyWhenNoSecretIsConfigured() {
        // Blank means a throwaway key, which is what lets a local run start bare.
        TestCaseCipher generated = new TestCaseCipher("");
        assertEquals("payload", generated.decrypt(generated.encrypt("payload")));

        // ...and it really is a different key from ours, not a hardcoded default.
        String encrypted = generated.encrypt("payload");
        assertThrows(IllegalArgumentException.class, () -> cipher.decrypt(encrypted));
    }

    @Test
    void generatedKeysDifferBetweenInstances() {
        String encrypted = new TestCaseCipher("").encrypt("payload");
        assertThrows(IllegalArgumentException.class, () -> new TestCaseCipher("").decrypt(encrypted));
    }

    @Test
    void acceptsASecretLongerThanTheKeyItNeeds() {
        // AES wants exactly 32 bytes, so a longer secret is cut down rather than
        // refused -- and cut the same way every time, or nothing would decrypt.
        String longSecret = Base64.getEncoder().encodeToString(
                "a-secret-that-runs-well-past-thirty-two-bytes".getBytes(StandardCharsets.UTF_8));

        TestCaseCipher first = new TestCaseCipher(longSecret);
        TestCaseCipher second = new TestCaseCipher(longSecret);
        assertEquals("payload", second.decrypt(first.encrypt("payload")));
    }
}
