package gdgrvce.iudex.server.model;

import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class SubmissionId implements Serializable {
    UUID userId;
    @Embedded
    ProblemId problemId;
    int submissionNum;

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        SubmissionId that = (SubmissionId) o;
        return submissionNum == that.submissionNum && Objects.equals(userId, that.userId) && Objects.equals(problemId, that.problemId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, problemId, submissionNum);
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public ProblemId getProblemId() {
        return problemId;
    }

    public void setProblemId(ProblemId problemId) {
        this.problemId = problemId;
    }

    public int getSubmissionNum() {
        return submissionNum;
    }

    public void setSubmissionNum(int submissionNum) {
        this.submissionNum = submissionNum;
    }
}
