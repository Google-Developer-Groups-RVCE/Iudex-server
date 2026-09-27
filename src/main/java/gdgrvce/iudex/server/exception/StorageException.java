package gdgrvce.iudex.server.exception;

/** Used when problem content cannot be read from or written to file storage. */
public class StorageException extends RuntimeException {
    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
