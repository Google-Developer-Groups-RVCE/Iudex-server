package gdgrvce.iudex.server.model;

import jakarta.persistence.*;

@Entity
@Table(name = "problem")
public class Problem {
    @EmbeddedId
    public ProblemId problemId;

    @MapsId("contestId")
    @ManyToOne Contest contest;

    @Column(nullable = false)
    int testCaseCount;

    public ProblemId getProblemId() {
        return problemId;
    }

    public void setProblemId(ProblemId problemId) {
        this.problemId = problemId;
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

