package gdg.iudex.repositories;

import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.time.OffsetDateTime;
import java.util.List;

/**
 *  interface RevokedTokenDao
 *  
 *  the data access object for revoked token
 *  
 *  revoke - adds a token to the revoked tokens table
 *  isRevoked - checks if a token has been revoked
 *  findActiveRevokedJtis - every revocation that still matters
 *  deleteExpiredTokens - clean up the database by removing expired tokens
 */

public interface RevokedTokenDao {

    @SqlUpdate("""
        MERGE INTO revoked_tokens (
            jti,
            user_id,
            expires_at
        )
        KEY (jti)
        VALUES (
            :jti,
            :userId,
            :expiresAt
        )
        """)
    void revoke(
        @Bind("jti") String jti,
        @Bind("userId") long userId,
        @Bind("expiresAt") OffsetDateTime expiresAt
    );

    @SqlQuery("""
        SELECT EXISTS (
            SELECT 1
            FROM revoked_tokens
            WHERE jti = :jti
        )
        """)
    boolean isRevoked(
        @Bind("jti") String jti
    );

    /*
     * Read once at startup (and after each purge) to populate
     * RevocationCache. Revocations for already-expired tokens are
     * skipped because such tokens are rejected on expiry anyway.
     */
    @SqlQuery("""
        SELECT jti
        FROM revoked_tokens
        WHERE expires_at > :now
        """)
    List<String> findActiveRevokedJtis(
        @Bind("now") OffsetDateTime now
    );

    @SqlUpdate("""
        DELETE FROM revoked_tokens
        WHERE expires_at <= CURRENT_TIMESTAMP
        """)
    int deleteExpiredTokens();
}
