package gdgrvce.iudex.server.service;

import gdgrvce.iudex.server.dto.ClientCompilation;
import gdgrvce.iudex.server.dto.ExecutionStatus;
import gdgrvce.iudex.server.dto.SubmissionResult;
import gdgrvce.iudex.server.model.TestCase;
import gdgrvce.iudex.server.model.Verdict;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Outputs are passed in the clear here; decryption is the identity. */
class GraderTests {

    private static final UnaryOperator<String> PLAIN = UnaryOperator.identity();

    private final TestCase sample = new TestCase("1 2", "3");
    private final TestCase hidden = new TestCase("5 5", "10");

    private Grader.Grade grade(SubmissionResult... results) {
        return Grader.grade(List.of(sample), List.of(hidden), null, List.of(results), PLAIN);
    }

    @Test
    void correctOutputsAreAccepted() {
        Grader.Grade grade = grade(new SubmissionResult(sample.id(), "3"), new SubmissionResult(hidden.id(), "10\n"));
        assertEquals(Verdict.ACCEPTED, grade.verdict());
        assertEquals(2, grade.passed());
    }

    @Test
    void aWrongSampleIsNamedButAWrongHiddenCaseIsNot() {
        assertEquals("Wrong answer on sample 1",
                grade(new SubmissionResult(sample.id(), "4"), new SubmissionResult(hidden.id(), "10")).message());
        assertEquals("Wrong answer on a hidden test case",
                grade(new SubmissionResult(sample.id(), "3"), new SubmissionResult(hidden.id(), "11")).message());
    }

    @Test
    void aReportedTimeoutFailsEvenWithTheRightOutput() {
        Grader.Grade grade = grade(new SubmissionResult(sample.id(), "3"),
                new SubmissionResult(hidden.id(), "10", ExecutionStatus.TIME_LIMIT_EXCEEDED));
        assertEquals(Verdict.TIME_LIMIT_EXCEEDED, grade.verdict());
        assertEquals(1, grade.passed());
    }

    @Test
    void repeatedUnknownOrMissingResultsAreNeverAccepted() {
        SubmissionResult right = new SubmissionResult(sample.id(), "3");
        assertEquals(Verdict.SYSTEM_ERROR, grade(right, right, new SubmissionResult(hidden.id(), "10")).verdict());
        assertEquals(Verdict.SYSTEM_ERROR, grade(right, new SubmissionResult(hidden.id(), "10"),
                new SubmissionResult(UUID.randomUUID(), "x")).verdict());
        assertEquals(Verdict.SYSTEM_ERROR, grade(right).verdict());
        assertEquals(Verdict.SYSTEM_ERROR, grade().verdict());
    }

    @Test
    void aCompileFailureKeepsBothStreams() {
        Grader.Grade grade = Grader.grade(List.of(sample), List.of(hidden),
                new ClientCompilation(false, "from stdout", "from stderr"), List.of(), PLAIN);
        assertEquals(Verdict.COMPILATION_ERROR, grade.verdict());
        assertEquals("from stderr\nfrom stdout", grade.compilerOutput());
    }
}
