package gdgrvce.iudex.server.exception;

/**
 * Used when an operation is refused because of where the contest sits in its
 * window: registering after it ends, editing problems once it has started,
 * reading problems before it starts.
 */
public class ContestStateException extends RuntimeException {
    public ContestStateException(String message) {
        super(message);
    }
}
