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
 *  verify - verifies a token
 *  revoke - revokes a token
 *  very obvious i know
 *  nothing else here
 *  implementation has its own tests
 */

public interface TokenService {

    String issue(User user);

    AuthenticatedUser verify(String token);

    void revoke(String token);
}