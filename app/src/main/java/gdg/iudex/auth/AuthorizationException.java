package gdg.iudex.auth;

import gdg.iudex.errors.ApiException;

/**
 *  AuthorizationException
 *
 *  "We know who you are, but you may not do this."
 *
 *  Distinct from AuthenticationException: a 401 means "log in",
 *  a 403 means "logging in again will not help".
 */

public final class AuthorizationException extends ApiException {

    public AuthorizationException() {
        this("You do not have permission to perform this action");
    }

    public AuthorizationException(String message) {
        super(403, message);
    }
}
