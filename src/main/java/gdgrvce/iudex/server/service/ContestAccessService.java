package gdgrvce.iudex.server.service;

import gdgrvce.iudex.server.exception.ContestStateException;
import gdgrvce.iudex.server.exception.ForbiddenOperationException;
import gdgrvce.iudex.server.model.Contest;
import gdgrvce.iudex.server.model.RegistrationId;
import gdgrvce.iudex.server.model.User;
import gdgrvce.iudex.server.repository.RegistrationRepository;
import gdgrvce.iudex.server.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/** Centralizes the contest access rules used by submission judging. */
@Service
public class ContestAccessService {

    private final UserRepository userRepository;
    private final RegistrationRepository registrationRepository;

    public ContestAccessService(UserRepository userRepository,
                                RegistrationRepository registrationRepository) {
        this.userRepository = userRepository;
        this.registrationRepository = registrationRepository;
    }

    public User currentUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ForbiddenOperationException("Unknown user"));
    }

    public void requireSubmissionAllowed(Contest contest, User user) {
        if (contest.getHostId().equals(user.getUserId())) {
            throw new ForbiddenOperationException("The contest host cannot submit to their own contest");
        }

        RegistrationId registrationId = new RegistrationId();
        registrationId.setContestId(contest.getContestId());
        registrationId.setUserId(user.getUserId());
        if (registrationRepository.findById(registrationId).isEmpty()) {
            throw new ForbiddenOperationException("You are not registered for this contest");
        }

        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(contest.getStartTime())) {
            throw new ContestStateException("This contest has not started yet");
        }
        if (!now.isBefore(contest.getEndTime())) {
            throw new ContestStateException("This contest has finished");
        }
    }
}