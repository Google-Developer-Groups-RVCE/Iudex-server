package gdgrvce.iudex.server.repository;

import gdgrvce.iudex.server.model.ProblemId;
import gdgrvce.iudex.server.model.Submission;
import gdgrvce.iudex.server.model.SubmissionId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface SubmissionRepository extends JpaRepository<Submission, SubmissionId> {

    @Query("select coalesce(max(s.submissionId.submissionNum), 0) from Submission s "
            + "where s.submissionId.problemId.contestId = :contestId "
            + "and s.submissionId.problemId.problemNum = :problemNum "
            + "and s.submissionId.userId = :userId")
    int findHighestSubmissionNum(@Param("contestId") UUID contestId,
                                 @Param("problemNum") int problemNum,
                                 @Param("userId") UUID userId);
}
