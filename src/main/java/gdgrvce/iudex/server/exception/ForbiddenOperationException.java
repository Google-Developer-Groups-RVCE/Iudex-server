package gdgrvce.iudex.server.exception;

/** Raised when an authenticated user is not allowed to perform an operation. */
public class ForbiddenOperationException extends RuntimeException {
    public ForbiddenOperationException(String message) {
        super(message);
    }
}