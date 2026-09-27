package gdgrvce.iudex.server.service;

import gdgrvce.iudex.server.dto.ContestRequest;
import gdgrvce.iudex.server.dto.ContestResponse;
import gdgrvce.iudex.server.exception.StorageException;
import gdgrvce.iudex.server.model.Contest;
import gdgrvce.iudex.server.model.User;
import gdgrvce.iudex.server.repository.ContestRepository;
import gdgrvce.iudex.server.repository.ProblemRepository;
import gdgrvce.iudex.server.repository.RegistrationRepository;
import gdgrvce.iudex.server.repository.SubmissionRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Create, read, update, and delete for contests. */
@Service
public class ContestService {

    private final ContestRepository contestRepository;
    private final ProblemRepository problemRepository;
    private final RegistrationRepository registrationRepository;
    private final SubmissionRepository submissionRepository;
    private final FileStorageService fileStorageService;
    private final ContestAccessService access;

    public ContestService(ContestRepository contestRepository,
                          ProblemRepository problemRepository,
                          RegistrationRepository registrationRepository,
                          SubmissionRepository submissionRepository,
                          FileStorageService fileStorageService,
                          ContestAccessService access) {
        this.contestRepository = contestRepository;
        this.problemRepository = problemRepository;
        this.registrationRepository = registrationRepository;
        this.submissionRepository = submissionRepository;
        this.fileStorageService = fileStorageService;
        this.access = access;
    }

    /** Creates a contest owned by the calling contestmaster. */
    @Transactional
    public ContestResponse create(UserDetails principal, ContestRequest request) {
        User user = access.currentUser(principal);
        access.requireCanCreateContests(user);
        validate(request.contestName(), request.startTime(), request.endTime());

        Contest contest = new Contest();
        contest.setContestName(request.contestName().trim());
        contest.setHostId(user.getUserId());
        contest.setStartTime(request.startTime());
        contest.setEndTime(request.endTime());
        Contest saved = contestRepository.save(contest);

        try {
            fileStorageService.createContest(saved);
        } catch (IOException exception) {
            throw new StorageException("Unable to create contest storage", exception);
        }

        return ContestResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<ContestResponse> list() {
        return contestRepository.findAllByOrderByStartTimeDesc().stream()
                .map(ContestResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ContestResponse get(UUID contestId) {
        return ContestResponse.from(access.requireContest(contestId));
    }

    /**
     * Applies the supplied fields. A null field is left alone, so the end time
     * can be moved while the contest runs without resending everything else.
     */
    @Transactional
    public ContestResponse update(UUID contestId, UserDetails principal, ContestRequest request) {
        User user = access.currentUser(principal);
        Contest contest = access.requireContest(contestId);
        access.requireContestOwner(contest, user);

        String name = request.contestName() == null ? contest.getContestName() : request.contestName().trim();
        OffsetDateTime start = request.startTime() == null ? contest.getStartTime() : request.startTime();
        OffsetDateTime end = request.endTime() == null ? contest.getEndTime() : request.endTime();
        validate(name, start, end);

        contest.setContestName(name);
        contest.setStartTime(start);
        contest.setEndTime(end);
        return ContestResponse.from(contestRepository.save(contest));
    }

    /**
     * Deletes a contest and everything under it. Allowed in any state.
     *
     * <p>The schema declares no cascading deletes, so the children are removed
     * in dependency order here: submissions, then registrations, then problems,
     * then the contest row, and finally its file storage.</p>
     */
    @Transactional
    public void delete(UUID contestId, UserDetails principal) {
        User user = access.currentUser(principal);
        Contest contest = access.requireContest(contestId);
        access.requireContestOwner(contest, user);

        submissionRepository.deleteByContestId(contestId);
        registrationRepository.deleteByContestId(contestId);
        problemRepository.deleteByContestId(contestId);
        contestRepository.delete(contest);

        try {
            if (fileStorageService.contestExists(contest)) {
                fileStorageService.deleteContest(contest);
            }
        } catch (IOException exception) {
            throw new StorageException("Unable to delete contest storage", exception);
        }
    }

    private void validate(String name, OffsetDateTime start, OffsetDateTime end) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("contestName must not be blank");
        }
        if (start == null || end == null) {
            throw new IllegalArgumentException("startTime and endTime are required");
        }
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("endTime must be after startTime");
        }
    }
}
