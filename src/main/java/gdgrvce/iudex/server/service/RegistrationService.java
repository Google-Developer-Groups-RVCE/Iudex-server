package gdgrvce.iudex.server.service;

import gdgrvce.iudex.server.dto.RegistrationResponse;
import gdgrvce.iudex.server.exception.ContestStateException;
import gdgrvce.iudex.server.exception.ForbiddenOperationException;
import gdgrvce.iudex.server.model.Contest;
import gdgrvce.iudex.server.model.Registration;
import gdgrvce.iudex.server.model.RegistrationId;
import gdgrvce.iudex.server.model.User;
import gdgrvce.iudex.server.repository.RegistrationRepository;
import gdgrvce.iudex.server.repository.SubmissionRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Self-registration, participant listing, and participant removal. */
@Service
public class RegistrationService {

    private final RegistrationRepository registrationRepository;
    private final SubmissionRepository submissionRepository;
    private final ContestAccessService access;

    public RegistrationService(RegistrationRepository registrationRepository,
                               SubmissionRepository submissionRepository,
                               ContestAccessService access) {
        this.registrationRepository = registrationRepository;
        this.submissionRepository = submissionRepository;
        this.access = access;
    }

    /**
     * Registers the calling user. Registration is always self-service: the
     * participant is the token subject, never a user named in the body.
     */
    @Transactional
    public RegistrationResponse register(UUID contestId, UserDetails principal) {
        User user = access.currentUser(principal);
        Contest contest = access.requireContest(contestId);
        access.requireNotContestOwner(contest, user);
        access.requireRegistrationOpen(contest);

        if (access.isRegistered(contest, user)) {
            throw new ContestStateException("Already registered for this contest");
        }

        RegistrationId registrationId = new RegistrationId();
        registrationId.setContestId(contestId);
        registrationId.setUserId(user.getUserId());

        Registration registration = new Registration();
        registration.setRegId(registrationId);
        registration.setContest(contest);
        registration.setUser(user);
        registration.setRegistrationTime(OffsetDateTime.now());

        return toResponse(registrationRepository.save(registration));
    }

    /** Lists participants. Open to the contestmaster and to anyone registered. */
    @Transactional(readOnly = true)
    public List<RegistrationResponse> list(UUID contestId, UserDetails principal) {
        User user = access.currentUser(principal);
        Contest contest = access.requireContest(contestId);
        if (!access.owns(contest, user) && !access.isAdmin(user) && !access.isRegistered(contest, user)) {
            throw new ForbiddenOperationException("You are not registered for this contest");
        }

        return registrationRepository.findByContestId(contestId).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Removes a participant. Either the participant themselves or the
     * contestmaster may do this.
     *
     * <p>The removal is a hard delete: the participant's submissions for this
     * contest go with it, so a later re-registration starts clean rather than
     * inheriting the earlier attempt history.</p>
     */
    @Transactional
    public void remove(UUID contestId, UUID userId, UserDetails principal) {
        User actor = access.currentUser(principal);
        Contest contest = access.requireContest(contestId);

        boolean removingSelf = actor.getUserId().equals(userId);
        if (!removingSelf && !access.owns(contest, actor) && !access.isAdmin(actor)) {
            throw new ForbiddenOperationException("Only the participant or the contestmaster may do that");
        }

        Registration registration = access.requireRegistration(contest, userId);
        submissionRepository.deleteByContestAndUser(contestId, userId);
        registrationRepository.delete(registration);
    }

    private RegistrationResponse toResponse(Registration registration) {
        return new RegistrationResponse(
                registration.getRegId().getContestId(),
                registration.getRegId().getUserId(),
                registration.getUser().getUsername(),
                registration.getRegistrationTime());
    }
}
