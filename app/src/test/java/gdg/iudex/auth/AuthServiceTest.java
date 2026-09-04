package gdg.iudex.auth;

import gdg.iudex.db.Database;
import gdg.iudex.models.Role;
import gdg.iudex.models.User;
import gdg.iudex.repositories.UserDao;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// DISCLAIMER: THIS IS PURELY VIBECODED, I RAN OUT OF PATIENCE

class AuthServiceTest {

    private Database database;
    private AuthService authService;

    // do this before each test
    @BeforeEach
    void setUp() {
        database = new Database("jdbc:h2:mem:auth_service_test");
        UserDao userDao = database.jdbi().onDemand(UserDao.class);

        userDao.insertUser("alice", "correct-hash", Role.CONTESTANT);

        PasswordHasher hasher = new PasswordHasher() {
            @Override
            public String hash(String password) {
                return "unused";
            }

            @Override
            public boolean verify(String password, String hash) {
                return "correct-password".equals(password) && "correct-hash".equals(hash);
            }
        };

        authService = new AuthService(userDao, hasher);
    }

    // close db after each
    @AfterEach
    void tearDown() {
        database.close();
    }

    @Test
    void authenticatesCorrectPassword() {
        User user = authService.authenticate("alice", "correct-password");
        
        assertEquals("alice", user.username());
        assertEquals(Role.CONTESTANT, user.role());
    }

    @Test
    void rejectsWrongPassword() {
        assertThrows(
            AuthenticationException.class,
            () -> authService.authenticate("alice", "wrong-password")
        );
    }

    /*
     * Argon2 is slow on purpose. If a missing username skipped it, a
     * failed login for a real account would be measurably slower than
     * one for an account that does not exist, and anyone timing the
     * responses could list our users. Counting the calls is a stable
     * way to check both paths do the same work.
     */
    @Test
    void unknownUsernameStillCostsAHash() {

        int[] verifyCalls = {0};

        UserDao emptyDao = database.jdbi().onDemand(UserDao.class);

        PasswordHasher counting = new PasswordHasher() {
            @Override
            public String hash(String password) {
                return "dummy-hash";
            }

            @Override
            public boolean verify(String password, String hash) {
                verifyCalls[0]++;
                return false;
            }
        };

        AuthService service = new AuthService(emptyDao, counting);

        int before = verifyCalls[0];

        assertThrows(
            AuthenticationException.class,
            () -> service.authenticate("does-not-exist", "whatever")
        );

        assertEquals(before + 1, verifyCalls[0],
            "A missing username must still pay for a hash check");
    }

    @Test
    void rejectsUnknownUsername() {
        assertThrows(
            AuthenticationException.class,
            () -> authService.authenticate("does-not-exist", "whatever")
        );
    }
}