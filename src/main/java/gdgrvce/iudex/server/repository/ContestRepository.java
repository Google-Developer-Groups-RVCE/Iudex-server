package gdgrvce.iudex.server.repository;

import gdgrvce.iudex.server.model.Contest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ContestRepository extends JpaRepository<Contest, UUID> {

}
