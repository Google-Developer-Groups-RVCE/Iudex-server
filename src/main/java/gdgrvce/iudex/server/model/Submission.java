package gdgrvce.iudex.server.model;

import jakarta.persistence.*;
import org.springframework.data.domain.Persistable;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One graded attempt at a problem.
 *
 * <p>Submissions are append-only: nothing ever edits one (apart from clearing
 * a superseded attempt's source, a bulk update that bypasses this entity), so {@link #isNew()}
 * is unconditionally true. Without that, Spring Data would see the assigned
 * id, treat the entity as detached, and save it with {@code merge}, costing a
 * select before every insert. Forcing {@code persist} also means a duplicate
 * attempt number fails on {@code uk_submission_attempt} rather than anything
 * being quietly merged.</p>
 */
@Entity
@Table(name = "submission")
public class Submission implements Persistable<UUID> {
    @Id
    @Column(name = "submission_id", nullable = false, updatable = false)
    private UUID submissionId;

    // The contestant's attempt number at this problem, starting from 1.
    @Column(nullable = false, updatable = false)
    private int submissionNum;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    User user;

    // Column order matches ProblemId's field order: contestId, then problemNum.
    // referencedColumnName is deliberately omitted -- Problem's own id is itself
    // derived via @MapsId, so those columns are not resolvable at this point.
    @ManyToOne(optional = false)
    @JoinColumns({
            @JoinColumn(name = "contest_id", nullable = false, updatable = false),
            @JoinColumn(name = "problem_num", nullable = false, updatable = false)
    })
    Problem problem;

    @Column(nullable = false)
    int passedTestCaseCount;

    // Server receipt time. This value, and never a client-supplied one, is the
    // submission time used for scoring.
    @Column(nullable = false)
    private OffsetDateTime receivedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Verdict verdict;

    @Column(nullable = false, updatable = false)
    private String message;

    @Column(nullable = false, updatable = false)
    private String language;

    // Kept only for the contestant's latest attempt at the problem; cleared on
    // older ones by SubmissionRepository.clearSourceOfEarlierAttempts.
    @Column
    private String sourceCode;

    @Column(updatable = false)
    private String compilerOutput;

    // Client-reported run time. Untrusted telemetry: it is displayable but must
    // never influence a verdict or a ranking.
    @Column
    private Integer clientDurationMs;

    @Override
    @Transient
    public UUID getId() {
        return submissionId;
    }

    /** Always true: a submission is inserted once and never updated. */
    @Override
    @Transient
    public boolean isNew() {
        return true;
    }

    public UUID getSubmissionId() {
        return submissionId;
    }

    public void setSubmissionId(UUID submissionId) {
        this.submissionId = submissionId;
    }

    public int getSubmissionNum() {
        return submissionNum;
    }

    public void setSubmissionNum(int submissionNum) {
        this.submissionNum = submissionNum;
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

    public Verdict getVerdict() {
        return verdict;
    }

    public void setVerdict(Verdict verdict) {
        this.verdict = verdict;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getSourceCode() {
        return sourceCode;
    }

    public void setSourceCode(String sourceCode) {
        this.sourceCode = sourceCode;
    }

    public String getCompilerOutput() {
        return compilerOutput;
    }

    public void setCompilerOutput(String compilerOutput) {
        this.compilerOutput = compilerOutput;
    }

    public Integer getClientDurationMs() {
        return clientDurationMs;
    }

    public void setClientDurationMs(Integer clientDurationMs) {
        this.clientDurationMs = clientDurationMs;
    }
}
