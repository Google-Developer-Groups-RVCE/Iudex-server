package gdgrvce.iudex.server.repository;

import gdgrvce.iudex.server.model.Registration;
import gdgrvce.iudex.server.model.RegistrationId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RegistrationRepository extends JpaRepository<Registration, RegistrationId> {

}
