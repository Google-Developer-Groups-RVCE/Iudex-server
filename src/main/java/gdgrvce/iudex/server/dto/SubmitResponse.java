package gdgrvce.iudex.server.dto;

/** The judged result of a submission. */
public record SubmitResponse(
        int submissionNum,
        int passedTestCaseCount,
        int totalTestCaseCount,
        String verdict) {
}
