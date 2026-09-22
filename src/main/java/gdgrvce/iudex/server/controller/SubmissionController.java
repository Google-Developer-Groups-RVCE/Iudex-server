package gdgrvce.iudex.server.controller;

import gdgrvce.iudex.server.dto.SubmitRequest;
import gdgrvce.iudex.server.dto.SubmitResponse;
import gdgrvce.iudex.server.service.SubmissionService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Accepts submissions for a contest problem and returns the judged result. */
@RestController
public class SubmissionController {
    private final SubmissionService submissionService;

    public SubmissionController(SubmissionService submissionService) {
        this.submissionService = submissionService;
    }

    /** Judges the submitted outputs against the problem's hidden testcases. */
    @PostMapping("/contests/{contestId}/problems/{problemNum}/submissions")
    public SubmitResponse submit(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable UUID contestId,
            @PathVariable int problemNum,
            @RequestBody SubmitRequest request) {
        return submissionService.judge(principal.getUsername(), contestId, problemNum, request);
    }
}
