package gdgrvce.iudex.server.controller;

import gdgrvce.iudex.server.dto.ContestRequest;
import gdgrvce.iudex.server.dto.ContestResponse;
import gdgrvce.iudex.server.dto.ProblemRequest;
import gdgrvce.iudex.server.dto.ProblemResponse;
import gdgrvce.iudex.server.dto.ProblemSummary;
import gdgrvce.iudex.server.dto.RegistrationResponse;
import gdgrvce.iudex.server.service.ContestService;
import gdgrvce.iudex.server.service.ProblemService;
import gdgrvce.iudex.server.service.RegistrationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Contest CRUD, plus the problem and participant collections under a contest. */
@RestController
@RequestMapping("/api/contests")
public class ContestController {

    private final ContestService contestService;
    private final ProblemService problemService;
    private final RegistrationService registrationService;

    public ContestController(ContestService contestService,
                             ProblemService problemService,
                             RegistrationService registrationService) {
        this.contestService = contestService;
        this.problemService = problemService;
        this.registrationService = registrationService;
    }

    @PostMapping
    public ResponseEntity<ContestResponse> create(@AuthenticationPrincipal UserDetails principal,
                                                  @RequestBody ContestRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(contestService.create(principal, request));
    }

    @GetMapping
    public List<ContestResponse> list() {
        return contestService.list();
    }

    @GetMapping("/{contestId}")
    public ContestResponse get(@PathVariable UUID contestId) {
        return contestService.get(contestId);
    }

    @PatchMapping("/{contestId}")
    public ContestResponse update(@PathVariable UUID contestId,
                                  @AuthenticationPrincipal UserDetails principal,
                                  @RequestBody ContestRequest request) {
        return contestService.update(contestId, principal, request);
    }

    @DeleteMapping("/{contestId}")
    public ResponseEntity<Void> delete(@PathVariable UUID contestId,
                                       @AuthenticationPrincipal UserDetails principal) {
        contestService.delete(contestId, principal);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{contestId}/problems")
    public ResponseEntity<ProblemResponse> addProblem(@PathVariable UUID contestId,
                                                      @AuthenticationPrincipal UserDetails principal,
                                                      @RequestBody ProblemRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(problemService.add(contestId, principal, request));
    }

    @GetMapping("/{contestId}/problems")
    public List<ProblemSummary> listProblems(@PathVariable UUID contestId,
                                             @AuthenticationPrincipal UserDetails principal) {
        return problemService.list(contestId, principal);
    }

    /** Self-registration. The participant is always the token subject. */
    @PostMapping("/{contestId}/registrations")
    public ResponseEntity<RegistrationResponse> register(@PathVariable UUID contestId,
                                                         @AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(registrationService.register(contestId, principal));
    }

    @GetMapping("/{contestId}/registrations")
    public List<RegistrationResponse> listParticipants(@PathVariable UUID contestId,
                                                       @AuthenticationPrincipal UserDetails principal) {
        return registrationService.list(contestId, principal);
    }

    @DeleteMapping("/{contestId}/registrations/{userId}")
    public ResponseEntity<Void> removeParticipant(@PathVariable UUID contestId,
                                                  @PathVariable UUID userId,
                                                  @AuthenticationPrincipal UserDetails principal) {
        registrationService.remove(contestId, userId, principal);
        return ResponseEntity.noContent().build();
    }
}
