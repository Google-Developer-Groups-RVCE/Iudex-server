package gdgrvce.iudex.server.repository;

import gdgrvce.iudex.server.model.Role;
import gdgrvce.iudex.server.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByUsername(String username);

    /** Tells the startup bootstrap whether an administrator already exists. */
    boolean existsByRole(Role role);

    List<User> findAllByOrderByUsername();
}
