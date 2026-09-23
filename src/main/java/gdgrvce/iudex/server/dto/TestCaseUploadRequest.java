package gdgrvce.iudex.server.dto;

import java.util.List;

/** Replaces a problem's sample and hidden test case collections. */
public record TestCaseUploadRequest(List<TestCaseData> sampleTestCases, List<TestCaseData> hiddenTestCases) {
}
