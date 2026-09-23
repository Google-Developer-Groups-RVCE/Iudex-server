package gdgrvce.iudex.server.repository;

import gdgrvce.iudex.server.model.Submission;
import gdgrvce.iudex.server.model.SubmissionId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface SubmissionRepository extends JpaRepository<Submission, SubmissionId> {

    /** Clears the submissions that a contest delete would otherwise orphan. */
    @Modifying
    @Query("delete from Submission s where s.submissionId.problemId.contestId = :contestId")
    void deleteByContestId(@Param("contestId") UUID contestId);

    @Modifying
    @Query("delete from Submission s where s.submissionId.problemId.contestId = :contestId "
            + "and s.submissionId.problemId.problemNum = :problemNum")
    void deleteByProblem(@Param("contestId") UUID contestId, @Param("problemNum") int problemNum);

    /** Removes a contestant's history when they leave or are removed. */
    @Modifying
    @Query("delete from Submission s where s.submissionId.problemId.contestId = :contestId "
            + "and s.submissionId.userId = :userId")
    void deleteByContestAndUser(@Param("contestId") UUID contestId, @Param("userId") UUID userId);
}
