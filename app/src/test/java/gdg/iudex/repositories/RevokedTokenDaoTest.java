package gdg.iudex.repositories;

import gdg.iudex.db.Database;
import org.jdbi.v3.core.statement.UnableToExecuteStatementException;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 *  class RevokedTokenDaoTest
 *  
 *  Unit tests for RevokedTokenDao
 *
 *  Tests:
 *  UnrevokedTokensWork - tests if isRevoked for an unrevoked token returns false.
 *  RevokedTokensWork - tests if isRevoked for a revoked token returns true.
 *  TokensAreIndependent - revoking one token does not affect another.
 *  RevokingSameTokenTwice - tests whether this is safe.
 *  NonexistentUser - a token that does not belong to any user cannot be revoked.
 *  DeleteExpiredTokenWorks - the cleanup of expired tokens works.
 */

class RevokedTokenDaoTest {

    @Test
    void UnrevokedTokensWork() {
    
        try(Database database =
                 new Database("jdbc:h2:mem:test_revoked_token_dao1")) {
                 
            RevokedTokenDao dao = database.jdbi()
                .onDemand(RevokedTokenDao.class);
        
            String newJti = UUID.randomUUID().toString();
        
            assertFalse(dao.isRevoked(newJti), "An unrevoked token should return false");
        }
    }

    @Test
    void RevokedTokensWork() {

        try(Database database =
                 new Database("jdbc:h2:mem:test_revoked_token_dao2")) {
                 
            RevokedTokenDao dao = database.jdbi()
                .onDemand(RevokedTokenDao.class);
        
            String jti = UUID.randomUUID().toString();
            OffsetDateTime expiresAt = OffsetDateTime.now().plusHours(1);
            long testUserId = 100L;

            database.jdbi().useHandle(handle -> {
                handle.createUpdate("""
                    INSERT INTO users
                        (id, username, password_hash, role)
                    VALUES
                        (:id, :username, :passwordHash, :role)
                    """)
                    .bind("id", testUserId)
                    .bind("username", "RH")
                    .bind("passwordHash", "iguessbro")
                    .bind("role", "CONTESTANT")
                    .execute();
            });

            dao.revoke(jti, testUserId, expiresAt);

            assertTrue(dao.isRevoked(jti), "A revoked token must return true");
        }
    }

    @Test
    void TokensAreIndependent() {

        try(Database database =
                 new Database("jdbc:h2:mem:test_revoked_token_dao3")) {
                 
            RevokedTokenDao dao = database.jdbi()
                .onDemand(RevokedTokenDao.class);
        
            String revokedJti = UUID.randomUUID().toString();
            String activeJti = UUID.randomUUID().toString();
            OffsetDateTime expiresAt = OffsetDateTime.now().plusHours(1);
            long testUserId = 100L;

            database.jdbi().useHandle(handle -> {
                handle.createUpdate("""
                    INSERT INTO users
                        (id, username, password_hash, role)
                    VALUES
                        (:id, :username, :passwordHash, :role)
                    """)
                    .bind("id", testUserId)
                    .bind("username", "RH")
                    .bind("passwordHash", "iguessbro")
                    .bind("role", "CONTESTANT")
                    .execute();
            });

            dao.revoke(revokedJti, testUserId, expiresAt);

            assertTrue(dao.isRevoked(revokedJti));
            assertFalse(dao.isRevoked(activeJti), "Unrelated token should remain valid");
        }
    }

    @Test
    void RevokingSameTokenTwice() {

        try(Database database =
                 new Database("jdbc:h2:mem:test_revoked_token_dao4")) {

            RevokedTokenDao dao = database.jdbi()
                .onDemand(RevokedTokenDao.class);
        
            String jti = UUID.randomUUID().toString();
            OffsetDateTime expiresAt = OffsetDateTime.now().plusHours(1);
            long testUserId = 100L;

            database.jdbi().useHandle(handle -> {
                handle.createUpdate("""
                    INSERT INTO users
                        (id, username, password_hash, role)
                    VALUES
                        (:id, :username, :passwordHash, :role)
                    """)
                    .bind("id", testUserId)
                    .bind("username", "RH")
                    .bind("passwordHash", "iguessbro")
                    .bind("role", "CONTESTANT")
                    .execute();
            });

            assertDoesNotThrow(() -> dao.revoke(jti, testUserId, expiresAt));
            assertDoesNotThrow(() -> dao.revoke(jti, testUserId, expiresAt), 
            "Revoking a token a second time should not throw an exception");

            assertTrue(dao.isRevoked(jti));
        }
    }


    @Test
    void NonexistentUser() {
        try(Database database =
                 new Database("jdbc:h2:mem:test_revoked_token_dao5")) {

            RevokedTokenDao dao = database.jdbi()
                .onDemand(RevokedTokenDao.class);
        
            String jti = UUID.randomUUID().toString();
            OffsetDateTime expiresAt = OffsetDateTime.now().plusHours(1);
            long fakeUserId = 999L;

            assertThrows(UnableToExecuteStatementException.class, 
                () -> dao.revoke(jti, fakeUserId, expiresAt));
        }
    }

    @Test
    void DeleteExpiredTokensWorks() {

        try(Database database =
                 new Database("jdbc:h2:mem:test_revoked_token_dao6")) {

            RevokedTokenDao dao = database.jdbi()
                .onDemand(RevokedTokenDao.class);

            String expiredJti = UUID.randomUUID().toString();
            String validJti = UUID.randomUUID().toString();
            long testUserId = 100L;

            database.jdbi().useHandle(handle -> {
                handle.createUpdate("""
                    INSERT INTO users
                        (id, username, password_hash, role)
                    VALUES
                        (:id, :username, :passwordHash, :role)
                    """)
                    .bind("id", testUserId)
                    .bind("username", "RH")
                    .bind("passwordHash", "iguessbro")
                    .bind("role", "CONTESTANT")
                    .execute();
            });

            OffsetDateTime past = OffsetDateTime.now(ZoneOffset.UTC).minusDays(1);
            dao.revoke(expiredJti, testUserId, past);

            OffsetDateTime future = OffsetDateTime.now(ZoneOffset.UTC).plusDays(1);
            dao.revoke(validJti, testUserId, future);

            int deletedRows = dao.deleteExpiredTokens();

            assertEquals(1, deletedRows, "Should have deleted exactly 1 expired token");
            assertFalse(dao.isRevoked(expiredJti), "Expired token should be purged from the DB");
            assertTrue(dao.isRevoked(validJti), "Future-dated token should remain in the DB");
        }
    }    
}