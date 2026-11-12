package gdgrvce.iudex.server.repository;

import gdgrvce.iudex.server.model.Role;
import gdgrvce.iudex.server.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByUsername(String username);

    /** Tells the startup bootstrap whether an administrator already exists. */
    boolean existsByRole(Role role);

    List<User> findAllByOrderByUsername();

    /**
     * Revokes every token issued to the user so far. Done as one update rather
     * than read-increment-save, so two logouts at once cannot both write the
     * same value.
     */
    @Modifying
    @Query("update User u set u.tokenVersion = u.tokenVersion + 1 where u.username = :username")
    int incrementTokenVersion(@Param("username") String username);
}
