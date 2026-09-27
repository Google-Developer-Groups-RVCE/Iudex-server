package gdgrvce.iudex.server.controller;

import gdgrvce.iudex.server.dto.ProblemRequest;
import gdgrvce.iudex.server.dto.ProblemResponse;
import gdgrvce.iudex.server.dto.EncryptedTestCase;
import gdgrvce.iudex.server.dto.TestCaseUploadRequest;
import gdgrvce.iudex.server.service.ProblemService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Problem reads and edits, addressed by contest and problem number.
 */
@RestController
@RequestMapping("/api/contests/{contestId}/problems/{problemNum}")
public class ProblemController {

    private final ProblemService problemService;

    public ProblemController(ProblemService problemService) {
        this.problemService = problemService;
    }

    @GetMapping
    public ProblemResponse get(@PathVariable UUID contestId,
                               @PathVariable int problemNum,
                               @AuthenticationPrincipal UserDetails principal) {
        return problemService.get(contestId, problemNum, principal);
    }

    @PatchMapping
    public ProblemResponse update(@PathVariable UUID contestId,
                                  @PathVariable int problemNum,
                                  @AuthenticationPrincipal UserDetails principal,
                                  @RequestBody ProblemRequest request) {
        return problemService.update(contestId, problemNum, principal, request);
    }

    @DeleteMapping
    public ResponseEntity<Void> delete(@PathVariable UUID contestId,
                                       @PathVariable int problemNum,
                                       @AuthenticationPrincipal UserDetails principal) {
        problemService.delete(contestId, problemNum, principal);
        return ResponseEntity.noContent().build();
    }

    /** Encrypted test inputs only. Expected output is not part of the response type. */
    @GetMapping("/tests")
    public List<EncryptedTestCase> tests(@PathVariable UUID contestId,
                                         @PathVariable int problemNum,
                                     @AuthenticationPrincipal UserDetails principal) {
        return problemService.testInputs(contestId, problemNum, principal);
    }

    @PutMapping("/testcases")
    public ProblemResponse replaceTestCases(@PathVariable UUID contestId,
                                            @PathVariable int problemNum,
                                            @AuthenticationPrincipal UserDetails principal,
                                            @RequestBody TestCaseUploadRequest request) {
        return problemService.replaceTestCases(contestId, problemNum, principal, request);
    }
}
