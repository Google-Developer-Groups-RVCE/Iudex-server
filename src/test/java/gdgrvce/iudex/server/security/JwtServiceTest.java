package gdgrvce.iudex.server.security;

import gdgrvce.iudex.server.model.Role;
import gdgrvce.iudex.server.model.User;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;

import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {
    private static final String SECRET = "/tmFBcHy/lwna805ZYUQnTTWFqxDb3N+eKWNJJnKBks=";

    private final JwtService jwtService = new JwtService(SECRET, 3600000L);

    private User sampleUser(String username) {
        User user = new User();
        user.setUserId(UUID.randomUUID());
        user.setUsername(username);
        user.setPasswordHash("hash");
        user.setRole(Role.CONTESTANT);
        return user;
    }

    @Test
    void generatedTokenCarriesTheUsername() {
        String token = jwtService.generateToken(sampleUser("alice"));
        assertEquals("alice", jwtService.extractUsername(token));
    }

    @Test
    void tokenIsValidForItsOwner() {
        String token = jwtService.generateToken(sampleUser("alice"));
        assertTrue(jwtService.isValid(token, "alice", 0));
    }

    @Test
    void tokenIsNotValidForAnotherUser() {
        String token = jwtService.generateToken(sampleUser("alice"));
        assertFalse(jwtService.isValid(token, "bob", 0));
    }

    @Test
    void tokenIsNotValidAfterItsVersionIsRevoked() {
        String token = jwtService.generateToken(sampleUser("alice"));
        assertFalse(jwtService.isValid(token, "alice", 1));
    }

    @Test
    void tokenCarriesTheVersionItWasIssuedUnder() {
        User user = sampleUser("alice");
        user.setTokenVersion(3);
        String token = jwtService.generateToken(user);
        assertTrue(jwtService.isValid(token, "alice", 3));
        assertFalse(jwtService.isValid(token, "alice", 2));
    }

    @Test
    void newUsersTokensStartAtVersionZero() {
        String token = jwtService.generateToken(sampleUser("alice"));
        assertTrue(jwtService.isValid(token, "alice", 0));
    }

    /** Tokens from before revocation existed carry no version to compare. */
    @Test
    void tokenWithoutAVersionIsRejected() {
        String legacy = Jwts.builder()
                .subject("alice")
                .claim("role", "CONTESTANT")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600000L))
                .signWith(new SecretKeySpec(Base64.getDecoder().decode(SECRET), "HmacSHA256"))
                .compact();

        assertEquals("alice", jwtService.extractUsername(legacy));
        assertFalse(jwtService.isValid(legacy, "alice", 0));
    }

    @Test
    void versionAndUsernameMustBothMatch() {
        User user = sampleUser("alice");
        user.setTokenVersion(2);
        String token = jwtService.generateToken(user);
        assertFalse(jwtService.isValid(token, "bob", 2));
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() {
        JwtService otherServer = new JwtService("aXVkZXgtdGVzdC1rZXktMzItYnl0ZXMtZXhhY3RseSE=", 3600000L);
        String token = otherServer.generateToken(sampleUser("alice"));
        assertThrows(JwtException.class, () -> jwtService.isValid(token, "alice", 0));
    }

    @Test
    void malformedTokenIsRejected() {
        assertThrows(JwtException.class, () -> jwtService.extractUsername("not.a.jwt"));
    }

    @Test
    void expiredTokenIsRejected() {
        JwtService shortLived = new JwtService(SECRET, -1000L);
        String token = shortLived.generateToken(sampleUser("alice"));
        assertThrows(JwtException.class, () -> jwtService.extractUsername(token));
    }
}
