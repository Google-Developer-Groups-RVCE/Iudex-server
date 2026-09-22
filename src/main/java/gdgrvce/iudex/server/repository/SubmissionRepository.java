package gdgrvce.iudex.server.repository;

import gdgrvce.iudex.server.model.ProblemId;
import gdgrvce.iudex.server.model.Submission;
import gdgrvce.iudex.server.model.SubmissionId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SubmissionRepository extends JpaRepository<Submission, SubmissionId> {

    long countBySubmissionIdUserIdAndSubmissionIdProblemId(UUID userId, ProblemId problemId);
}
