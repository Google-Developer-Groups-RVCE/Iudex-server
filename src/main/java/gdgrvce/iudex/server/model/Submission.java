package gdgrvce.iudex.server.model;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "submission")
public class Submission {
    @EmbeddedId
    public SubmissionId submissionId;

    // Flat identifier returned by POST /api/submissions. Unique, but not the
    // primary key.
    @Column(name = "submission_id", nullable = false, unique = true, updatable = false)
    private UUID submissionUuid;

    @MapsId("userId")
    @ManyToOne
    @JoinColumn(name = "user_id")
    User user;

    @MapsId("problemId")
    @ManyToOne
    @JoinColumns({
            @JoinColumn(name = "problem_num", referencedColumnName = "problem_num"),
            @JoinColumn(name = "contest_id", referencedColumnName = "contest_id")
    })
    Problem problem;

    @Column(nullable = false)
    int passedTestCaseCount;

    // Server receipt time. This value, and never a client-supplied one, is the
    // submission time used for scoring.
    @Column(nullable = false)
    private OffsetDateTime receivedAt;

    // Client-reported run time. Untrusted telemetry: it is displayable but must
    // never influence a verdict or a ranking.
    @Column
    private Integer clientDurationMs;

    public SubmissionId getSubmissionId() {
        return submissionId;
    }

    public void setSubmissionId(SubmissionId submissionId) {
        this.submissionId = submissionId;
    }

    public UUID getSubmissionUuid() {
        return submissionUuid;
    }

    public void setSubmissionUuid(UUID submissionUuid) {
        this.submissionUuid = submissionUuid;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Problem getProblem() {
        return problem;
    }

    public void setProblem(Problem problem) {
        this.problem = problem;
    }

    public int getPassedTestCaseCount() {
        return passedTestCaseCount;
    }

    public void setPassedTestCaseCount(int passedTestCaseCount) {
        this.passedTestCaseCount = passedTestCaseCount;
    }

    public OffsetDateTime getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(OffsetDateTime receivedAt) {
        this.receivedAt = receivedAt;
    }

    public Integer getClientDurationMs() {
        return clientDurationMs;
    }

    public void setClientDurationMs(Integer clientDurationMs) {
        this.clientDurationMs = clientDurationMs;
    }
}
