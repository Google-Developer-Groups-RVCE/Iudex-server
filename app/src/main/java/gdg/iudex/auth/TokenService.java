package gdg.iudex.auth;

import gdg.iudex.models.User;

/**
 *  interface TokenService
 *
 *  Higher layers shouldn't care how the token service is implemented
 *  so it doesn't really matter how we implement it.
 *
 *  Methods:
 *  issue - issues a token to a user
 *  verify - verifies a token, returning who it belongs to
 *  revoke - revokes an already-verified token
 *  implementation has its own tests
 */

public interface TokenService {

    String issue(User user);

    AuthenticatedUser verify(String token);

    /*
     * Takes the result of verify() rather than the raw token: the
     * caller has always verified it already, and re-parsing would
     * mean doing the same signature check twice per logout.
     */
    void revoke(AuthenticatedUser user);
}
