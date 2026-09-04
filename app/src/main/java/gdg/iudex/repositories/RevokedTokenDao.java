package gdg.iudex.repositories;

import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import java.time.OffsetDateTime;

/**
 *  interface RevokedTokenDao
 *  
 *  the data access object for revoked token
 *  
 *  revoke - adds a token to the revoked tokens table
 *  isRevoked - checks if a token has been revoked
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

    @SqlUpdate("""
        DELETE FROM revoked_tokens
        WHERE expires_at <= CURRENT_TIMESTAMP
        """)
    int deleteExpiredTokens();
}