package gdg.iudex.auth;

import gdg.iudex.repositories.RevokedTokenDao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 *  class RevocationCache
 *
 *  Keeps the set of revoked token ids in memory so verifying a token
 *  does not cost a database round trip on every single request.
 *
 *  This is safe to cache because the set only ever grows through
 *  revoke() below, which is the only writer in the application. The
 *  database stays the source of truth and is written first, so a
 *  restart rebuilds the cache with nothing lost.
 *
 *  NOTE: correct for a single server process. Running two instances
 *  against one database would need shared state (or a short token
 *  lifetime) instead, since a logout on one instance would not reach
 *  the other's cache.
 *
 *  Methods:
 *  isRevoked - has this token id been revoked (no database access)
 *  revoke - record a revocation, database first, then cache
 *  purgeExpired - drop revocations whose tokens have expired anyway
 */

public final class RevocationCache {

    private static final Logger log =
            LoggerFactory.getLogger(RevocationCache.class);

    private final RevokedTokenDao revokedTokenDao;

    // Replaced wholesale by purgeExpired, read without locking.
    private volatile Set<String> revokedJtis = Set.of();

    // Serialises the two writers against each other, so a revocation
    // can never be dropped by a purge that started before it.
    private final Object writeLock = new Object();

    public RevocationCache(RevokedTokenDao revokedTokenDao) {
        this.revokedTokenDao = revokedTokenDao;
        reload();
    }

    public boolean isRevoked(String jti) {
        return revokedJtis.contains(jti);
    }

    public void revoke(String jti, long userId, Instant expiresAt) {
        synchronized (writeLock) {
            revokedTokenDao.revoke(
                jti,
                userId,
                expiresAt.atOffset(ZoneOffset.UTC)
            );

            revokedJtis.add(jti);
        }
    }

    /**
     *  Deletes revocations for tokens that have already expired.
     *
     *  Once a token is past its expiry it is rejected on expiry alone,
     *  so its revocation record can never change an outcome again.
     *  Without this the table (and this cache) would grow forever.
     */
    public int purgeExpired() {
        synchronized (writeLock) {
            int deleted = revokedTokenDao.deleteExpiredTokens();
            reload();

            if (deleted > 0) {
                log.info("Purged {} expired token revocation(s)", deleted);
            }

            return deleted;
        }
    }

    private void reload() {
        Set<String> fresh = ConcurrentHashMap.newKeySet();

        fresh.addAll(revokedTokenDao.findActiveRevokedJtis(
            OffsetDateTime.now(ZoneOffset.UTC)
        ));

        revokedJtis = fresh;
    }
}
