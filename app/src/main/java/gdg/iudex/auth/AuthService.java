package gdg.iudex.auth;

import gdg.iudex.models.User;
import gdg.iudex.repositories.UserDao;

import java.util.Optional;

/**
 *  class AuthService
 *
 *  provides basic authentication services
 *  
 *  Methods:
 *  authenticate - check if a user exists in the database, 
 *  if yes, then check if the passwords match
 */
public final class AuthService {

    private final UserDao userDao;
    private final PasswordHasher passwordHasher;

    /*
     * A throwaway hash, verified against whenever the username does
     * not exist. Argon2 is slow on purpose, so returning early on an
     * unknown username would make a failed login for a real account
     * measurably slower than one for an account that does not exist,
     * and anyone timing the responses could list our users.
     * Verifying this instead makes both paths cost the same.
     */
    private final String dummyHash;

    public AuthService(
            UserDao userDao,
            PasswordHasher passwordHasher) {

        this.userDao = userDao;
        this.passwordHasher = passwordHasher;
        this.dummyHash = passwordHasher.hash("no-such-user");
    }

    public User authenticate(
            String username,
            String password) {

        Optional<User> found = userDao.findByUsername(username);

        if (found.isEmpty()) {
            passwordHasher.verify(password, dummyHash);
            throw new AuthenticationException();
        }

        User user = found.get();

        if (!passwordHasher.verify(
                password,
                user.passwordHash())) {

            throw new AuthenticationException();
        }

        return user;
    }
}
