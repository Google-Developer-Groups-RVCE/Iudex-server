package gdgrvce.iudex.server.exception;

/** Used when the caller is authenticated but not allowed to do this. */
public class ForbiddenOperationException extends RuntimeException {
    public ForbiddenOperationException(String message) {
        super(message);
    }
}
