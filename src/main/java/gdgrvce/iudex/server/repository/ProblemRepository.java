package gdgrvce.iudex.server.repository;

import gdgrvce.iudex.server.model.Problem;
import gdgrvce.iudex.server.model.ProblemId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProblemRepository extends JpaRepository<Problem, ProblemId> {
}
