package gdgrvce.iudex.server.exception;

/** Raised when persisted contest data cannot be read or written. */
public class StorageException extends RuntimeException {
    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}