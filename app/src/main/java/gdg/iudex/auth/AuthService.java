package gdg.iudex.auth;

import gdg.iudex.models.User;
import gdg.iudex.repositories.UserDao;

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

    public AuthService(
            UserDao userDao,
            PasswordHasher passwordHasher) {

        this.userDao = userDao;
        this.passwordHasher = passwordHasher;
    }

    public User authenticate(
            String username,
            String password) {

        User user = userDao
                .findByUsername(username)
                .orElseThrow(AuthenticationException::new);

        if (!passwordHasher.verify(
                password,
                user.passwordHash())) {

            throw new AuthenticationException();
        }

        return user;
    }
}