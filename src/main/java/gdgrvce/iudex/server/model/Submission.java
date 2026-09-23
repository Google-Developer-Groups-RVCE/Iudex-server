package gdgrvce.iudex.server.model;

import jakarta.persistence.*;
import org.springframework.data.domain.Persistable;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One graded attempt at a problem.
 *
 * <p>Submissions are append-only: nothing ever edits one, so {@link #isNew()}
 * is unconditionally true. Without that, Spring Data would see the assigned
 * composite key, treat the entity as detached, and save it with {@code merge},
 * which turns a duplicate attempt number into a silent overwrite of an earlier
 * submission. Forcing {@code persist} makes the collision fail on the primary
 * key instead, where the caller can see it.</p>
 */
@Entity
@Table(name = "submission")
public class Submission implements Persistable<SubmissionId> {
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

    // Column order matches ProblemId's field order: contestId, then problemNum.
    // referencedColumnName is deliberately omitted -- Problem's own id is itself
    // derived via @MapsId, so those columns are not resolvable at this point.
    @MapsId("problemId")
    @ManyToOne
    @JoinColumns({
            @JoinColumn(name = "contest_id"),
            @JoinColumn(name = "problem_num")
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

    @Override
    @Transient
    public SubmissionId getId() {
        return submissionId;
    }

    /** Always true: a submission is inserted once and never updated. */
    @Override
    @Transient
    public boolean isNew() {
        return true;
    }

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
