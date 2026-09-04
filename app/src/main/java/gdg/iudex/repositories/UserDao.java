package gdg.iudex.repositories;

import gdg.iudex.models.User;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.util.Optional;

/**
 *  interface UserDao
 *  
 *  the user data access object
 *  
 *  findByUsername - finds a user by their username.
 *  insertUser - you could manually do this, but this is slightly better.
 */

public interface UserDao {

    @SqlQuery("""
        SELECT
            id,
            username,
            password_hash AS passwordHash,
            role,
            created_at AS createdAt
        FROM users
        WHERE username = :username
        """)
    @RegisterConstructorMapper(User.class)
    Optional<User> findByUsername(
        @Bind("username") String username
    );

    @SqlUpdate("""
        INSERT INTO users (username, password_hash, role)
        VALUES (:username, :passwordHash, :role)
        """)
    @GetGeneratedKeys("id")
    long insertUser(
        @Bind("username") String username,
        @Bind("passwordHash") String passwordHash,
        @Bind("role") String role
    );
}