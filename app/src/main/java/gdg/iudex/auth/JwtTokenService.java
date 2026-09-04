package gdg.iudex.auth;

import gdg.iudex.models.Role;
import gdg.iudex.models.User;
import gdg.iudex.repositories.RevokedTokenDao;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.UUID;

public final class JwtTokenService implements TokenService {

    private final SecretKey signingKey;
    private final Duration tokenLifetime;
    private final RevokedTokenDao revokedTokenDao;

    // this constructor is for testing, to avoid a bit of hassle
    public JwtTokenService(
            SecretKey signingKey,
            Duration tokenLifetime,
            RevokedTokenDao revokedTokenDao) {

        this.signingKey = signingKey;
        this.tokenLifetime = tokenLifetime;
        this.revokedTokenDao = revokedTokenDao;
    }

    // this is the constructor you must use in production
    public JwtTokenService(
            Duration tokenLifetime,
            RevokedTokenDao revokedTokenDao) {

        // you must have an env variable called JWT_SECRET in order to use this constructor
        String secret = System.getenv("JWT_SECRET");

        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                "JWT_SECRET environment variable is not set"
            );
        }

        this.signingKey = Keys.hmacShaKeyFor(
            secret.getBytes(StandardCharsets.UTF_8)
        );

        this.tokenLifetime = tokenLifetime;
        this.revokedTokenDao = revokedTokenDao;
    }

    @Override
    public String issue(User user) {

        Instant now = Instant.now();
        Instant expiration = now.plus(tokenLifetime);

        // i really hope this is doing everything that's required
        // okay at second glance it's just setting all the fields
        return Jwts.builder()
            .subject(Long.toString(user.id()))
            .claim("role", user.role().name())
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiration))
            .id(UUID.randomUUID().toString())
            .signWith(signingKey)
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
            if (revokedTokenDao.isRevoked(jti)) {
                throw new AuthenticationException("Token has been revoked");
            }

            long userId = Long.parseLong(claims.getSubject());
            Role role = Role.valueOf(claims.get("role", String.class));

            return new AuthenticatedUser(userId, role);

        } catch (io.jsonwebtoken.JwtException e) {
            throw new AuthenticationException("Invalid or expired token", e);
        }
    }

    @Override
    public void revoke(String token) {
        Claims claims;
        try {
            claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
                
        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            // if expired, you add to revocation list
            claims = e.getClaims();
            
        } catch (io.jsonwebtoken.JwtException e) {
            // invalid token
            throw new AuthenticationException("Cannot revoke invalid token", e);
        }

        String jti = claims.getId();
        long userId = Long.parseLong(claims.getSubject());
        Instant expiration = claims.getExpiration().toInstant();

        revokedTokenDao.revoke(
            jti,
            userId,
            expiration.atOffset(ZoneOffset.UTC)
        );
    }
}