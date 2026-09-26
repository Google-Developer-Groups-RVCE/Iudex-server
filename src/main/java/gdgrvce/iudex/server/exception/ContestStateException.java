package gdgrvce.iudex.server.exception;

/** Raised when a contest's current state prevents an operation. */
public class ContestStateException extends RuntimeException {
    public ContestStateException(String message) {
        super(message);
    }
}