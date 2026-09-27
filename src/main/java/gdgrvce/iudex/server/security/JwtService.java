package gdgrvce.iudex.server.security;

import gdgrvce.iudex.server.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Date;

/** Creates and checks the JWTs used to keep users signed in. */
@Service
public class JwtService {

    /** HS256 needs a key at least as long as its 256-bit output. */
    private static final int KEY_BYTES = 32;

    private final SecretKey signingKey;
    private final long expirationMs;

    /** A blank secret is replaced by a throwaway key so a local run needs no setup. */
    public JwtService(@Value("${jwt.secret:}") String secret,
                      @Value("${jwt.expiration-ms:86400000}") long expirationMs) {
        this.signingKey = new SecretKeySpec(
                SecretKeys.resolve(secret, KEY_BYTES, "jwt.secret"), "HmacSHA256");
        this.expirationMs = expirationMs;
    }

    /** Creates a signed token containing the user's basic auth details. */
    public String generateToken(User user) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);
        return Jwts.builder()
                .subject(user.getUsername())
                .claim("role", user.getRole().name())
                .claim("userId", user.getUserId().toString())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
    }

    /** Reads the username from a token, or fails if the token cannot be trusted. */
    public String extractUsername(String token) {
        return parseClaims(token).getSubject();
    }

    /** Checks that the token belongs to this user and has not expired. */
    public boolean isValid(String token, String username) {
        Claims claims = parseClaims(token);
        return claims.getSubject().equals(username) && claims.getExpiration().after(new Date());
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
