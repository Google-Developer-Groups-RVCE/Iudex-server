package gdgrvce.iudex.server.dto;

import java.util.UUID;

/** The source code of a submission, for its author or the contestmaster. */
public record SubmissionSource(UUID submissionId, String language, String sourceCode) {
}
