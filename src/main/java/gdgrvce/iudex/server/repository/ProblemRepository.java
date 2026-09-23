package gdgrvce.iudex.server.repository;

import gdgrvce.iudex.server.model.Problem;
import gdgrvce.iudex.server.model.ProblemId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProblemRepository extends JpaRepository<Problem, ProblemId> {

    /** Resolves the flat identifier used by {@code GET /api/problems/{id}}. */
    Optional<Problem> findByProblemUuid(UUID problemUuid);

    @Query("select p from Problem p where p.problemId.contestId = :contestId order by p.problemId.problemNum")
    List<Problem> findByContestId(@Param("contestId") UUID contestId);

    @Query("select coalesce(max(p.problemId.problemNum), 0) from Problem p where p.problemId.contestId = :contestId")
    int findHighestProblemNum(@Param("contestId") UUID contestId);

    @Modifying
    @Query("delete from Problem p where p.problemId.contestId = :contestId")
    void deleteByContestId(@Param("contestId") UUID contestId);
}
