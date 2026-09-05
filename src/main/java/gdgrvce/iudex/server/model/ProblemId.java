package gdgrvce.iudex.server.model;

import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class ProblemId implements Serializable {
    UUID contestId;
    int problemNum;

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        ProblemId problemId = (ProblemId) o;
        return problemNum == problemId.problemNum && Objects.equals(contestId, problemId.contestId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(contestId, problemNum);
    }

    public UUID getContestId() {
        return contestId;
    }

    public void setContestId(UUID contestId) {
        this.contestId = contestId;
    }

    public int getProblemNum() {
        return problemNum;
    }

    public void setProblemNum(int problemNum) {
        this.problemNum = problemNum;
    }
}
