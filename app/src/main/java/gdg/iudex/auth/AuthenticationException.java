package gdg.iudex.auth;

/**
 *  AuthenticationException
 *  
 *  A kind of runtimeException.
 *  Just includes constructors for whatever usage.
 */

public final class AuthenticationException
        extends RuntimeException {

    public AuthenticationException() {
        super("Invalid credentials");
    }

    public AuthenticationException(String s) {
        super(s);
    }

    public AuthenticationException(String s, io.jsonwebtoken.JwtException e) {
        super(s, e);
    }
}