package gdgrvce.iudex.server.model;

import jakarta.persistence.*;

@Entity
@Table(name = "submission")
public class Submission {
    @EmbeddedId
    public SubmissionId submissionId;

    @MapsId("userId")
    @ManyToOne User user;

    @MapsId("problemId")
    @ManyToOne Problem problem;

    @Column(nullable = false)
    int passedTestCaseCount;

    public SubmissionId getSubmissionId() {
        return submissionId;
    }

    public void setSubmissionId(SubmissionId submissionId) {
        this.submissionId = submissionId;
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
}

