package gdgrvce.iudex.server.service;

import gdgrvce.iudex.server.exception.ContestStateException;
import gdgrvce.iudex.server.exception.ForbiddenOperationException;
import gdgrvce.iudex.server.exception.ResourceNotFoundException;
import gdgrvce.iudex.server.model.Contest;
import gdgrvce.iudex.server.model.Problem;
import gdgrvce.iudex.server.model.Registration;
import gdgrvce.iudex.server.model.RegistrationId;
import gdgrvce.iudex.server.model.Role;
import gdgrvce.iudex.server.model.User;
import gdgrvce.iudex.server.repository.ContestRepository;
import gdgrvce.iudex.server.repository.ProblemRepository;
import gdgrvce.iudex.server.repository.RegistrationRepository;
import gdgrvce.iudex.server.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * The single place that decides who may do what, and when.
 *
 * <p>Contest state is derived from the server clock, so the rules read
 * directly off the contest window:</p>
 *
 * <ul>
 *   <li>Register: before the contest ends.</li>
 *   <li>Edit problems and test cases: before the contest starts.</li>
 *   <li>Read problems: once the contest has started.</li>
 *   <li>Delete: at any time.</li>
 * </ul>
 *
 * <p>Every rule lives here rather than in the controllers, so a new endpoint
 * cannot accidentally skip one.</p>
 */
@Service
public class ContestAccessService {

    private final UserRepository userRepository;
    private final ContestRepository contestRepository;
    private final ProblemRepository problemRepository;
    private final RegistrationRepository registrationRepository;

    public ContestAccessService(UserRepository userRepository,
                                ContestRepository contestRepository,
                                ProblemRepository problemRepository,
                                RegistrationRepository registrationRepository) {
        this.userRepository = userRepository;
        this.contestRepository = contestRepository;
        this.problemRepository = problemRepository;
        this.registrationRepository = registrationRepository;
    }

    /** Resolves the token subject. No request body may name a different user. */
    public User currentUser(UserDetails principal) {
        if (principal == null) {
            throw new ForbiddenOperationException("Authentication required");
        }
        return userRepository.findByUsername(principal.getUsername())
                .orElseThrow(() -> new ForbiddenOperationException("Unknown user"));
    }

    public Contest requireContest(UUID contestId) {
        return contestRepository.findById(contestId)
                .orElseThrow(() -> new ResourceNotFoundException("No such contest: " + contestId));
    }

    public Problem requireProblem(UUID problemUuid) {
        return problemRepository.findByProblemUuid(problemUuid)
                .orElseThrow(() -> new ResourceNotFoundException("No such problem: " + problemUuid));
    }

    public boolean isAdmin(User user) {
        return user.getRole() == Role.ADMIN;
    }

    public boolean owns(Contest contest, User user) {
        return user.getUserId().equals(contest.getHostId());
    }

    /** Allows the owning contestmaster, or any administrator. */
    public void requireContestOwner(Contest contest, User user) {
        if (!owns(contest, user) && !isAdmin(user)) {
            throw new ForbiddenOperationException("Only the contestmaster of this contest may do that");
        }
    }

    /** Allows only a contestmaster or administrator to create contests. */
    public void requireCanCreateContests(User user) {
        if (user.getRole() == Role.CONTESTANT) {
            throw new ForbiddenOperationException("Contestants may not create contests");
        }
    }

    public boolean isRegistered(Contest contest, User user) {
        RegistrationId registrationId = new RegistrationId();
        registrationId.setContestId(contest.getContestId());
        registrationId.setUserId(user.getUserId());
        return registrationRepository.findById(registrationId).isPresent();
    }

    public Registration requireRegistration(Contest contest, UUID userId) {
        RegistrationId registrationId = new RegistrationId();
        registrationId.setContestId(contest.getContestId());
        registrationId.setUserId(userId);
        return registrationRepository.findById(registrationId)
                .orElseThrow(() -> new ResourceNotFoundException("Not registered for this contest"));
    }

    /** Registration closes when the contest ends. */
    public void requireRegistrationOpen(Contest contest) {
        if (!OffsetDateTime.now().isBefore(contest.getEndTime())) {
            throw new ContestStateException("Registration has closed for this contest");
        }
    }

    /** The contestmaster holds the answers, so they cannot compete. */
    public void requireNotContestOwner(Contest contest, User user) {
        if (owns(contest, user)) {
            throw new ForbiddenOperationException("The contestmaster cannot register for their own contest");
        }
    }

    /** Problems and test cases are frozen once the contest starts. */
    public void requireProblemsEditable(Contest contest) {
        if (!OffsetDateTime.now().isBefore(contest.getStartTime())) {
            throw new ContestStateException("Problems cannot be changed once the contest has started");
        }
    }

    /**
     * Contestants may read problems only once the contest has started, and only
     * if they are registered. The contestmaster and administrators may always
     * read, so a contest can be prepared before it opens.
     */
    public void requireProblemReadable(Contest contest, User user) {
        if (owns(contest, user) || isAdmin(user)) {
            return;
        }
        if (!isRegistered(contest, user)) {
            throw new ForbiddenOperationException("You are not registered for this contest");
        }
        if (OffsetDateTime.now().isBefore(contest.getStartTime())) {
            throw new ContestStateException("This contest has not started yet");
        }
    }
}
