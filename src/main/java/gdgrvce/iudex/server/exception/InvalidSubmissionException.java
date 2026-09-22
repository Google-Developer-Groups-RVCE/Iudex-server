package gdgrvce.iudex.server.exception;

/** Raised when a submission's shape does not match the problem, such as the wrong output count. */
public class InvalidSubmissionException extends RuntimeException {
    public InvalidSubmissionException(String message) {
        super(message);
    }
}
