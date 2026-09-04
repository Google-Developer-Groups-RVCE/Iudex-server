package gdg.iudex.repositories;

import gdg.iudex.db.Database;
import gdg.iudex.models.Role;
import gdg.iudex.models.User;
import org.junit.jupiter.api.Test;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 *  class UserDaoTest
 *  
 *  Unit tests for UserDao.
 *
 *  Tests:
 *  findByUsername - Check if a manually inserted user can be found by username.
 *  insertUser - test insertUser and whether the inserted user can be found.
 *  everyRoleRoundTrips - each Role survives a write and a read.
 */

class UserDaoTest {

    @Test
    void findByUsername() {

        try (Database database =
                 new Database("jdbc:h2:mem:test_user_dao_find;DB_CLOSE_DELAY=-1")) {

            UserDao dao = database.jdbi()
                .onDemand(UserDao.class);

            // this manually inserts a new user RH.
            database.jdbi().useHandle(handle -> {
                handle.createUpdate("""
                    INSERT INTO users
                        (username, password_hash, role)
                    VALUES
                        (:username, :passwordHash, :role)
                    """)
                    .bind("username", "RH")
                    .bind("passwordHash", "iguessbro")
                    .bind("role", "CONTESTANT")
                    .execute();
            });

            // now actually test findByUsername
            Optional<User> result = dao.findByUsername("RH");

            // make sure we got something
            assertTrue(result.isPresent());

            User user = result.get();

            // make sure nothing changed
            assertEquals("RH", user.username());
            assertEquals("iguessbro", user.passwordHash());
            assertEquals(Role.CONTESTANT, user.role());
            assertNotNull(user.createdAt());
            assertTrue(user.id() > 0);
        }
    }

    @Test
    void findByUsernameNegative() {

        try (Database database =
                new Database("jdbc:h2:mem:test_user_dao_missing;DB_CLOSE_DELAY=-1")) {

            UserDao dao = database.jdbi()
                .onDemand(UserDao.class);

            // don't insert anything
            // this should be empty
            Optional<User> result = dao.findByUsername("udit_h");

            assertTrue(result.isEmpty());
        }
    }

    @Test
    void insertUser() {

        try (Database database =
                new Database("jdbc:h2:mem:test_user_dao_insert;DB_CLOSE_DELAY=-1")) {

            UserDao dao = database.jdbi()
                .onDemand(UserDao.class);

            // insert chandra :>
            long id = dao.insertUser(
                "chandra",
                "alfreddabuttler",
                Role.CONTESTANT
            );

            assertTrue(id > 0);

            Optional<User> result = dao.findByUsername("chandra");

            assertTrue(result.isPresent());

            User user = result.get();

            // make sure everything's fine
            assertEquals(id, user.id());
            assertEquals("chandra", user.username());
            assertEquals("alfreddabuttler", user.passwordHash());
            assertEquals(Role.CONTESTANT, user.role());
            assertNotNull(user.createdAt());
        }
    }

    @Test
    void everyRoleRoundTrips() {

        // insertUser takes a Role rather than a String, so a bad role
        // cannot reach the database at all. This checks the ones that
        // can still each survive the trip.
        try (Database database =
                new Database("jdbc:h2:mem:test_user_dao_roles;DB_CLOSE_DELAY=-1")) {

            UserDao dao = database.jdbi()
                .onDemand(UserDao.class);

            for (Role role : Role.values()) {

                String username = "user_" + role.name();

                dao.insertUser(username, "hash", role);

                Optional<User> result = dao.findByUsername(username);

                assertTrue(result.isPresent());
                assertEquals(role, result.get().role(),
                    "Role " + role + " should come back as it went in");
            }
        }
    }
}
