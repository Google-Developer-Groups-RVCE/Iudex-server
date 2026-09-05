package gdgrvce.iudex.server.repository;

import gdgrvce.iudex.server.model.Submission;
import gdgrvce.iudex.server.model.SubmissionId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubmissionRepository extends JpaRepository<Submission, SubmissionId> {
}
