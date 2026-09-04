package gdg.iudex.auth;

import gdg.iudex.models.Role;
import gdg.iudex.models.User;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.MacAlgorithm;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

public final class JwtTokenService implements TokenService {

    /*
     * Pinned, not inferred. Keys.hmacShaKeyFor picks HS256/HS384/HS512
     * purely from how many bytes the secret happens to be, which means
     * two deployments of identical code could sign with different
     * algorithms just because someone typed a longer passphrase.
     */
    private static final MacAlgorithm ALGORITHM = Jwts.SIG.HS256;

    /** The JCA name for the key HS256 signs with. */
    private static final String KEY_ALGORITHM = "HmacSHA256";

    /** Shortest JWT_SECRET we accept, in bytes. */
    public static final int MIN_SECRET_LENGTH = 32;

    private final SecretKey signingKey;
    private final Duration tokenLifetime;
    private final RevocationCache revocationCache;

    // this constructor is for testing, to avoid a bit of hassle
    public JwtTokenService(
            SecretKey signingKey,
            Duration tokenLifetime,
            RevocationCache revocationCache) {

        this.signingKey = signingKey;
        this.tokenLifetime = tokenLifetime;
        this.revocationCache = revocationCache;
    }

    // this is the constructor you must use in production
    public JwtTokenService(
            String secret,
            Duration tokenLifetime,
            RevocationCache revocationCache) {

        this.signingKey = keyFrom(secret);
        this.tokenLifetime = tokenLifetime;
        this.revocationCache = revocationCache;
    }

    /**
     *  Turns the configured secret into a key of exactly the size the
     *  pinned algorithm wants.
     *
     *  The secret must still be long enough to be worth anything, but
     *  hashing it means any acceptable secret produces a valid HS256
     *  key, so secret length can no longer change the algorithm.
     */
    private static SecretKey keyFrom(String secret) {

        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                "JWT_SECRET environment variable is not set"
            );
        }

        byte[] raw = secret.getBytes(StandardCharsets.UTF_8);

        if (raw.length < MIN_SECRET_LENGTH) {
            throw new IllegalStateException(
                "JWT_SECRET must be at least " + MIN_SECRET_LENGTH
                + " bytes long, but was " + raw.length
            );
        }

        try {
            byte[] keyBytes = MessageDigest
                .getInstance("SHA-256")
                .digest(raw);

            return new SecretKeySpec(keyBytes, KEY_ALGORITHM);

        } catch (NoSuchAlgorithmException e) {
            // Every JVM is required to ship SHA-256.
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    @Override
    public String issue(User user) {

        Instant now = Instant.now();
        Instant expiration = now.plus(tokenLifetime);

        return Jwts.builder()
            .subject(Long.toString(user.id()))
            .claim("role", user.role().name())
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiration))
            .id(UUID.randomUUID().toString())
            .signWith(signingKey, ALGORITHM)
            .compact();
    }

    // this just authenticates a user by verifying that their token hasn't been revoked
    @Override
    public AuthenticatedUser verify(String token) {
        try {
            Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

            String jti = claims.getId();

            // In-memory check, so this costs no database round trip.
            if (revocationCache.isRevoked(jti)) {
                throw new AuthenticationException("Token has been revoked");
            }

            long userId = Long.parseLong(claims.getSubject());
            Role role = Role.valueOf(claims.get("role", String.class));

            return new AuthenticatedUser(
                userId,
                role,
                jti,
                claims.getExpiration().toInstant()
            );

        } catch (io.jsonwebtoken.JwtException e) {
            throw new AuthenticationException("Invalid or expired token", e);
        }
    }

    @Override
    public void revoke(AuthenticatedUser user) {
        revocationCache.revoke(
            user.tokenId(),
            user.userId(),
            user.expiresAt()
        );
    }
}
