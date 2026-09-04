package gdg.iudex.auth;

import gdg.iudex.db.Database;
import gdg.iudex.models.Role;
import gdg.iudex.models.User;
import gdg.iudex.repositories.RevokedTokenDao;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 *  class JwtSecretTest
 *
 *  Tests for how JwtTokenService turns the configured secret into a
 *  signing key, using the production constructor.
 *
 *  The algorithm used to be inferred from the secret's length, so two
 *  deployments of identical code could sign with different algorithms
 *  purely because someone typed a longer passphrase. It is pinned now,
 *  and these hold it there.
 *
 *  Tests:
 *  algorithmIsAlwaysHs256 - whatever the secret's length.
 *  tokensVerifyWithTheSameSecret - the happy path still works.
 *  shortSecretIsRejected - below the documented minimum.
 *  blankSecretIsRejected - and so is a missing one.
 *  differentSecretsCannotVerifyEachOther - the key really is derived
 *      from the secret and not from something constant.
 *  invalidTokenKeepsItsCause - the underlying failure is not lost.
 */

class JwtSecretTest {

    private Database database;
    private RevocationCache cache;

    private static final User USER = new User(
        42L, "udit", "basement", Role.CONTESTANT, OffsetDateTime.now()
    );

    private static final String SECRET_32 =
        "0123456789abcdef0123456789abcdef";

    @BeforeEach
    void setUp() {
        database = new Database("jdbc:h2:mem:" + UUID.randomUUID());
        cache = new RevocationCache(
            database.jdbi().onDemand(RevokedTokenDao.class));
    }

    @AfterEach
    void tearDown() {
        database.close();
    }

    private JwtTokenService serviceWith(String secret) {
        return new JwtTokenService(secret, Duration.ofMinutes(15), cache);
    }

    /** The "alg" value out of a token's header segment. */
    private static String algorithmOf(String token) {
        String header = new String(
            Base64.getUrlDecoder().decode(token.split("\\.")[0]),
            StandardCharsets.UTF_8
        );

        assertTrue(header.contains("\"alg\""), "No alg in header: " + header);

        return header.replaceAll(".*\"alg\"\\s*:\\s*\"([^\"]+)\".*", "$1");
    }

    @Test
    void algorithmIsAlwaysHs256() {

        // 32, 48 and 70 bytes. Previously these produced HS256, HS384
        // and HS512 respectively.
        String[] secrets = {
            SECRET_32,
            SECRET_32 + "0123456789abcdef",
            SECRET_32 + SECRET_32 + "012456"
        };

        for (String secret : secrets) {
            String token = serviceWith(secret).issue(USER);

            assertEquals("HS256", algorithmOf(token),
                "A " + secret.length() + " byte secret must still sign "
                + "with the pinned algorithm");
        }
    }

    @Test
    void tokensVerifyWithTheSameSecret() {
        JwtTokenService service = serviceWith(SECRET_32);

        AuthenticatedUser verified = service.verify(service.issue(USER));

        assertEquals(42L, verified.userId());
        assertEquals(Role.CONTESTANT, verified.role());
        assertNotNull(verified.tokenId());
        assertNotNull(verified.expiresAt());
    }

    @Test
    void shortSecretIsRejected() {
        String tooShort = "0123456789abcdef0123456789abcde"; // 31 bytes

        assertEquals(31, tooShort.length());

        IllegalStateException thrown = assertThrows(
            IllegalStateException.class,
            () -> serviceWith(tooShort)
        );

        assertTrue(thrown.getMessage().contains("32"),
            "The error should say what the minimum is: "
            + thrown.getMessage());
    }

    @Test
    void blankSecretIsRejected() {
        assertThrows(IllegalStateException.class, () -> serviceWith(null));
        assertThrows(IllegalStateException.class, () -> serviceWith(""));
        assertThrows(IllegalStateException.class, () -> serviceWith("   "));
    }

    @Test
    void differentSecretsCannotVerifyEachOther() {
        String token = serviceWith(SECRET_32).issue(USER);

        JwtTokenService other =
            serviceWith("fedcba9876543210fedcba9876543210");

        assertThrows(
            AuthenticationException.class,
            () -> other.verify(token),
            "A token signed with another secret must not verify"
        );
    }

    @Test
    void invalidTokenKeepsItsCause() {
        AuthenticationException thrown = assertThrows(
            AuthenticationException.class,
            () -> serviceWith(SECRET_32).verify("not-a-jwt")
        );

        assertEquals(401, thrown.status());
        assertNotNull(thrown.getCause(),
            "The underlying failure should be kept for the logs");
    }
}
