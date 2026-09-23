package gdgrvce.iudex.server.repository;

import gdgrvce.iudex.server.model.Submission;
import gdgrvce.iudex.server.model.SubmissionId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubmissionRepository extends JpaRepository<Submission, SubmissionId> {

    /** Resolves the flat identifier used by {@code GET /api/submissions/{id}}. */
    Optional<Submission> findBySubmissionUuid(UUID submissionUuid);

    /**
     * The attempt number the next submission should take.
     *
     * <p>Numbering runs per contestant per problem, so two people racing on the
     * same problem never contend for a number.</p>
     */
    @Query("select coalesce(max(s.submissionId.submissionNum), 0) from Submission s "
            + "where s.submissionId.problemId.contestId = :contestId "
            + "and s.submissionId.problemId.problemNum = :problemNum "
            + "and s.submissionId.userId = :userId")
    int findHighestSubmissionNum(@Param("contestId") UUID contestId,
                                 @Param("problemNum") int problemNum,
                                 @Param("userId") UUID userId);

    /** One contestant's attempt history for a problem, oldest first. */
    @Query("select s from Submission s "
            + "where s.submissionId.problemId.contestId = :contestId "
            + "and s.submissionId.problemId.problemNum = :problemNum "
            + "and s.submissionId.userId = :userId "
            + "order by s.submissionId.submissionNum")
    List<Submission> findByProblemAndUser(@Param("contestId") UUID contestId,
                                          @Param("problemNum") int problemNum,
                                          @Param("userId") UUID userId);

    /** Every submission in a contest, for the contestmaster's review. */
    @Query("select s from Submission s where s.submissionId.problemId.contestId = :contestId "
            + "order by s.receivedAt")
    List<Submission> findByContestId(@Param("contestId") UUID contestId);

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
