package gdgrvce.iudex.server.security;

import gdgrvce.iudex.server.model.Role;
import gdgrvce.iudex.server.model.User;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

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
        user.setRole(Role.PARTICIPANT);
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
        assertTrue(jwtService.isValid(token, "alice"));
    }

    @Test
    void tokenIsNotValidForAnotherUser() {
        String token = jwtService.generateToken(sampleUser("alice"));
        assertFalse(jwtService.isValid(token, "bob"));
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
