package gdg.iudex.auth;

import gdg.iudex.models.Role;
import gdg.iudex.models.User;
import gdg.iudex.db.Database;
import gdg.iudex.repositories.RevokedTokenDao;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.*;

/*
 *  JwtTokenServiceTest
 *
 *  Unit tests for JwtTokenService.
 *
 *  Tests:
 *  issuesToken - issue properly issues a token
 *  issuedTokenVerifies - issue returns the correct authenticated user
 *  expiredTokenIsRejected - duh
 *  modifiedSignatureIsRejected - a tampered signature is refused
 *  modifiedPayloadIsRejected - tampered claims are refused
 *  tokenSignedWithDifferentKeyIsRejected - name is way too long
 *  malformedTokenIsRejected - ...
 */

class JwtTokenServiceTest {

    private static final SecretKey TEST_KEY =
        Jwts.SIG.HS256.key().build();
    
    Database database =
                new Database(
                    "jdbc:h2:mem:jwt_token_service_test");

    private final RevocationCache cache =
                new RevocationCache(
                    database.jdbi()
                        .onDemand(RevokedTokenDao.class));

    private final JwtTokenService tokenService =
        new JwtTokenService(
            TEST_KEY,
            Duration.ofMinutes(15),
            cache
        );

    private final User user = new User(
        42L,
        "udit",
        "basement",
        Role.CONTESTANT,
        OffsetDateTime.now()
    );

    @Test
    void issuesToken() {

        String token = tokenService.issue(user);

        assertNotNull(token);
        assertFalse(token.isBlank());
    }

    @Test
    void issuedTokenVerifies() {

        String token = tokenService.issue(user);

        AuthenticatedUser authenticated =
            tokenService.verify(token);

        assertEquals(42L, authenticated.userId());
        assertEquals(Role.CONTESTANT, authenticated.role());
    }

    @Test
    void expiredTokenIsRejected() throws InterruptedException {

        JwtTokenService shortLivedService =
            new JwtTokenService(
                TEST_KEY,
                Duration.ofMillis(50),
                cache
            );

        String token = shortLivedService.issue(user);

        Thread.sleep(100);

        assertThrows(
            Exception.class,
            () -> shortLivedService.verify(token)
        );
    }

    /*
     * Tampering must change bits the signature actually covers.
     * Editing the LAST character of a segment is not enough: a 32 byte
     * HS256 signature base64url-encodes to 43 characters carrying 258
     * bits, so the final character's low 2 bits are padding and are
     * thrown away on decode. 'Y', 'Z', 'a' and 'b' all decode alike
     * there, which made an earlier version of this test pass or fail
     * depending on the last character of a randomly generated
     * signature. The first character of a segment is always fully
     * significant, so mutating that is deterministic.
     */
    private static String tamperWith(String token, int segment) {

        String[] parts = token.split("\\.");

        char first = parts[segment].charAt(0);
        parts[segment] = (first == 'A' ? 'B' : 'A') + parts[segment].substring(1);

        return String.join(".", parts);
    }

    @Test
    void modifiedSignatureIsRejected() {

        String modifiedToken = tamperWith(tokenService.issue(user), 2);

        assertThrows(
            Exception.class,
            () -> tokenService.verify(modifiedToken)
        );
    }

    @Test
    void modifiedPayloadIsRejected() {

        String modifiedToken = tamperWith(tokenService.issue(user), 1);

        assertThrows(
            Exception.class,
            () -> tokenService.verify(modifiedToken)
        );
    }

    @Test
    void tokenSignedWithDifferentKeyIsRejected() {

        String token = tokenService.issue(user);

        SecretKey differentKey =
            Keys.hmacShaKeyFor(new byte[] {
                1, 2, 3, 4, 5, 6, 7, 8,
                9, 10, 11, 12, 13, 14, 15, 16,
                17, 18, 19, 20, 21, 22, 23, 24,
                25, 26, 27, 28, 29, 30, 31, 32
            });

        JwtTokenService differentService =
            new JwtTokenService(
                differentKey,
                Duration.ofMinutes(15),
                cache
            );

        assertThrows(
            Exception.class,
            () -> differentService.verify(token)
        );
    }

    @Test
    void malformedTokenIsRejected() {

        assertThrows(
            Exception.class,
            () -> tokenService.verify("not-a-jwt")
        );
    }
}