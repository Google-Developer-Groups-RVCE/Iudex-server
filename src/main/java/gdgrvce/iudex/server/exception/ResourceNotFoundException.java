package gdgrvce.iudex.server.exception;

/** Used when a requested contest, problem, or registration does not exist. */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
