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

        userDao.insertUser("alice", "correct-hash", "CONTESTANT");

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

    @Test
    void rejectsUnknownUsername() {
        assertThrows(
            AuthenticationException.class,
            () -> authService.authenticate("does-not-exist", "whatever")
        );
    }
}