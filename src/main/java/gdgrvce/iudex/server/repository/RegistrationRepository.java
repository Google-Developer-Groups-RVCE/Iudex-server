package gdgrvce.iudex.server.repository;

import gdgrvce.iudex.server.model.Registration;
import gdgrvce.iudex.server.model.RegistrationId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface RegistrationRepository extends JpaRepository<Registration, RegistrationId> {

    @Query("select r from Registration r where r.regId.contestId = :contestId order by r.registrationTime")
    List<Registration> findByContestId(@Param("contestId") UUID contestId);

    @Modifying
    @Query("delete from Registration r where r.regId.contestId = :contestId")
    void deleteByContestId(@Param("contestId") UUID contestId);
}
