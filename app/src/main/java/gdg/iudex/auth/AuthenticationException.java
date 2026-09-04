package gdg.iudex.auth;

import gdg.iudex.errors.ApiException;

/**
 *  AuthenticationException
 *
 *  "We do not know who you are."
 *
 *  Always reported as 401. The message is deliberately vague on the
 *  login path so it cannot be used to tell a missing user apart from
 *  a wrong password.
 */

public final class AuthenticationException extends ApiException {

    public AuthenticationException() {
        this("Invalid credentials");
    }

    public AuthenticationException(String message) {
        super(401, message);
    }

    // Takes Throwable rather than a library-specific type so the
    // token implementation stays hidden from everything above it.
    public AuthenticationException(String message, Throwable cause) {
        super(401, message, cause);
    }
}
