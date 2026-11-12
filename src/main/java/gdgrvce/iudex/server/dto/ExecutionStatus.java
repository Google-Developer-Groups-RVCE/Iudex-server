package gdgrvce.iudex.server.dto;

/**
 * How the client says one test case run ended, before any output comparison.
 *
 * <p>Untrusted: every value other than {@link #SUCCESS} can only fail a
 * submission. A missing status is read as {@link #SUCCESS}, which is safe for
 * the same reason -- the output still has to match.</p>
 */
public enum ExecutionStatus {
    SUCCESS,
    RUNTIME_ERROR,
    TIME_LIMIT_EXCEEDED,
    MEMORY_LIMIT_EXCEEDED,
    OUTPUT_LIMIT_EXCEEDED
}
