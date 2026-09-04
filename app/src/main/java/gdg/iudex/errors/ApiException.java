package gdg.iudex.errors;

/**
 *  class ApiException
 *
 *  Base class for every error this API reports to a client.
 *
 *  Carrying the HTTP status on the exception itself means a single
 *  handler can render all of them in one consistent JSON shape,
 *  instead of each failure inventing its own format.
 *
 *  @status - the HTTP status this error should be reported as
 */

public class ApiException extends RuntimeException {

    private final int status;

    public ApiException(int status, String message) {
        super(message);
        this.status = status;
    }

    public ApiException(int status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public int status() {
        return status;
    }
}
