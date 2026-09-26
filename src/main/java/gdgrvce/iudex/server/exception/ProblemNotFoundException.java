package gdgrvce.iudex.server.exception;

/** Raised when a submission targets a problem that does not exist. */
public class ProblemNotFoundException extends RuntimeException {
    public ProblemNotFoundException(String message) {
        super(message);
    }
}
