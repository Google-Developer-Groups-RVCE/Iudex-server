package gdg.iudex.auth;

import gdg.iudex.db.Database;
import gdg.iudex.repositories.RevokedTokenDao;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 *  class RevocationCacheTest
 *
 *  Unit tests for RevocationCache.
 *
 *  The cache exists so verifying a token costs no database round trip.
 *  That is only safe if the database stays the source of truth, so
 *  most of these check the two cannot drift apart.
 *
 *  Tests:
 *  unknownTokenIsNotRevoked - the empty case.
 *  revokedTokenIsKnownImmediately - without re-reading the database.
 *  revocationReachesTheDatabase - it is not cached and forgotten.
 *  revocationSurvivesARestart - a fresh cache reloads it.
 *  expiredRevocationsAreNotLoaded - they can no longer change anything.
 *  purgeExpiredKeepsLiveRevocations - and drops only the dead ones.
 *  revokingTwiceIsSafe - logout twice must not blow up.
 */

class RevocationCacheTest {

    private Database database;
    private RevokedTokenDao dao;

    private static final long USER_ID = 100L;

    @BeforeEach
    void setUp() {
        // A private database per test so tests cannot collide.
        database = new Database("jdbc:h2:mem:" + UUID.randomUUID());
        dao = database.jdbi().onDemand(RevokedTokenDao.class);

        database.jdbi().useHandle(handle ->
            handle.createUpdate("""
                INSERT INTO users
                    (id, username, password_hash, role)
                VALUES
                    (:id, :username, :passwordHash, :role)
                """)
                .bind("id", USER_ID)
                .bind("username", "RH")
                .bind("passwordHash", "iguessbro")
                .bind("role", "CONTESTANT")
                .execute());
    }

    @AfterEach
    void tearDown() {
        database.close();
    }

    private static Instant inAnHour() {
        return Instant.now().plus(1, ChronoUnit.HOURS);
    }

    @Test
    void unknownTokenIsNotRevoked() {
        RevocationCache cache = new RevocationCache(dao);

        assertFalse(cache.isRevoked(UUID.randomUUID().toString()));
    }

    @Test
    void revokedTokenIsKnownImmediately() {
        RevocationCache cache = new RevocationCache(dao);
        String jti = UUID.randomUUID().toString();

        cache.revoke(jti, USER_ID, inAnHour());

        assertTrue(cache.isRevoked(jti));
        assertFalse(cache.isRevoked(UUID.randomUUID().toString()),
            "Revoking one token must not affect another");
    }

    @Test
    void revocationReachesTheDatabase() {
        RevocationCache cache = new RevocationCache(dao);
        String jti = UUID.randomUUID().toString();

        cache.revoke(jti, USER_ID, inAnHour());

        assertTrue(dao.isRevoked(jti),
            "The database is the source of truth and must be written");
    }

    @Test
    void revocationSurvivesARestart() {
        String jti = UUID.randomUUID().toString();

        new RevocationCache(dao).revoke(jti, USER_ID, inAnHour());

        // A second cache over the same database stands in for a
        // restarted server rebuilding its state.
        RevocationCache afterRestart = new RevocationCache(dao);

        assertTrue(afterRestart.isRevoked(jti),
            "A logged out token must stay logged out across a restart");
    }

    @Test
    void expiredRevocationsAreNotLoaded() {
        String expiredJti = UUID.randomUUID().toString();

        dao.revoke(
            expiredJti,
            USER_ID,
            Instant.now().minus(1, ChronoUnit.DAYS)
                .atOffset(java.time.ZoneOffset.UTC)
        );

        RevocationCache cache = new RevocationCache(dao);

        assertFalse(cache.isRevoked(expiredJti),
            "An expired token is refused on expiry, so its revocation "
            + "is dead weight and should not be held in memory");
    }

    @Test
    void purgeExpiredKeepsLiveRevocations() {
        RevocationCache cache = new RevocationCache(dao);

        String liveJti = UUID.randomUUID().toString();
        String expiredJti = UUID.randomUUID().toString();

        cache.revoke(liveJti, USER_ID, inAnHour());
        cache.revoke(expiredJti, USER_ID,
            Instant.now().minus(1, ChronoUnit.DAYS));

        int deleted = cache.purgeExpired();

        assertEquals(1, deleted, "Only the expired revocation should go");
        assertTrue(cache.isRevoked(liveJti),
            "A live revocation must survive the purge");
        assertFalse(dao.isRevoked(expiredJti),
            "The expired row should be gone from the database too");
    }

    @Test
    void revokingTwiceIsSafe() {
        RevocationCache cache = new RevocationCache(dao);
        String jti = UUID.randomUUID().toString();

        assertDoesNotThrow(() -> cache.revoke(jti, USER_ID, inAnHour()));
        assertDoesNotThrow(() -> cache.revoke(jti, USER_ID, inAnHour()));

        assertTrue(cache.isRevoked(jti));
    }
}
