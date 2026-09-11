package gdgrvce.iudex.server.service;

/** Used when registration tries to claim a username that is already in use. */
public class UsernameAlreadyExistsException extends RuntimeException {
    public UsernameAlreadyExistsException(String username) {
        super("Username already taken: " + username);
    }
}
