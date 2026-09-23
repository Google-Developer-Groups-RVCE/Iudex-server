package gdgrvce.iudex.server.controller;

import gdgrvce.iudex.server.dto.SubmissionRequest;
import gdgrvce.iudex.server.dto.SubmissionResponse;
import gdgrvce.iudex.server.service.SubmissionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Submitting an attempt and reading back what it scored.
 *
 * <p>There is no update or delete here: a graded submission is a matter of
 * record.</p>
 */
@RestController
@RequestMapping("/api")
public class SubmissionController {

    private final SubmissionService submissionService;

    public SubmissionController(SubmissionService submissionService) {
        this.submissionService = submissionService;
    }

    /** Grades the reported outputs and records the attempt. */
    @PostMapping("/submissions")
    public ResponseEntity<SubmissionResponse> submit(@AuthenticationPrincipal UserDetails principal,
                                                     @RequestBody SubmissionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(submissionService.submit(principal, request));
    }

    @GetMapping("/submissions/{submissionId}")
    public SubmissionResponse get(@PathVariable UUID submissionId,
                                  @AuthenticationPrincipal UserDetails principal) {
        return submissionService.get(submissionId, principal);
    }

    /** The caller's own attempts at a problem, oldest first. */
    @GetMapping("/problems/{problemId}/submissions")
    public List<SubmissionResponse> listMine(@PathVariable UUID problemId,
                                             @AuthenticationPrincipal UserDetails principal) {
        return submissionService.listMine(problemId, principal);
    }

    /** Every attempt in a contest. Contestmaster and administrators only. */
    @GetMapping("/contests/{contestId}/submissions")
    public List<SubmissionResponse> listForContest(@PathVariable UUID contestId,
                                                   @AuthenticationPrincipal UserDetails principal) {
        return submissionService.listForContest(contestId, principal);
    }
}
