package gdgrvce.iudex.server.model;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "problem")
public class Problem {
    @EmbeddedId
    public ProblemId problemId;

    // Flat identifier for GET /api/problems/{id}, and the file storage
    // directory name. Unique, but not the primary key.
    @Column(name = "problem_id", nullable = false, unique = true, updatable = false)
    private UUID problemUuid;

    @MapsId("contestId")
    @ManyToOne
    @JoinColumn(name = "contest_id")
    Contest contest;

    @Column(nullable = false)
    int testCaseCount;

    public ProblemId getProblemId() {
        return problemId;
    }

    public void setProblemId(ProblemId problemId) {
        this.problemId = problemId;
    }

    public UUID getProblemUuid() {
        return problemUuid;
    }

    public void setProblemUuid(UUID problemUuid) {
        this.problemUuid = problemUuid;
    }

    public Contest getContest() {
        return contest;
    }

    public void setContest(Contest contest) {
        this.contest = contest;
    }

    public int getTestCaseCount() {
        return testCaseCount;
    }

    public void setTestCaseCount(int testCaseCount) {
        this.testCaseCount = testCaseCount;
    }
}
