package gdgrvce.iudex.server.model;

/**
 * The server's authoritative judgement of a submission.
 *
 * <p>Only {@link #ACCEPTED} is ever derived from comparing outputs. The
 * client-reported failure kinds can only ever fail a submission, so a modified
 * client can use them to fail itself but never to pass.</p>
 */
public enum Verdict {
    ACCEPTED,
    WRONG_ANSWER,
    COMPILATION_ERROR,
    RUNTIME_ERROR,
    TIME_LIMIT_EXCEEDED,
    MEMORY_LIMIT_EXCEEDED,
    OUTPUT_LIMIT_EXCEEDED,
    SYSTEM_ERROR
}
