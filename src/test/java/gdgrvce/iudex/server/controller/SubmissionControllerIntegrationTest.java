package gdgrvce.iudex.server.controller;

import gdgrvce.iudex.server.dto.RegisterRequest;
import gdgrvce.iudex.server.dto.SubmitRequest;
import gdgrvce.iudex.server.model.Contest;
import gdgrvce.iudex.server.model.Problem;
import gdgrvce.iudex.server.model.ProblemId;
import gdgrvce.iudex.server.model.Registration;
import gdgrvce.iudex.server.model.RegistrationId;
import gdgrvce.iudex.server.model.TestCase;
import gdgrvce.iudex.server.repository.ContestRepository;
import gdgrvce.iudex.server.repository.ProblemRepository;
import gdgrvce.iudex.server.repository.RegistrationRepository;
import gdgrvce.iudex.server.repository.UserRepository;
import gdgrvce.iudex.server.service.FileStorageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the submit flow through the real security, database, and file
 * storage.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SubmissionControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ContestRepository contestRepository;

    @Autowired
    private ProblemRepository problemRepository;

    @Autowired
    private RegistrationRepository registrationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FileStorageService fileStorageService;

    @DynamicPropertySource
    static void storageRoot(DynamicPropertyRegistry registry) throws IOException {
        String root = Files.createTempDirectory("iudex-submission-test").toString();
        registry.add("iudex.storage.root", () -> root);
    }

    @Test
    void allCorrectOutputsPass() throws Exception {
        String token = register("solve_user");
        UUID contestId = seedProblem("solve_user", List.of(new TestCase("1 2", "3"), new TestCase("4 5", "9")));

        submit(token, contestId, 1, List.of("3", "9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.submissionNum").value(1))
                .andExpect(jsonPath("$.passedTestCaseCount").value(2))
                .andExpect(jsonPath("$.totalTestCaseCount").value(2))
                .andExpect(jsonPath("$.verdict").value("PASS"));
    }

    @Test
    void partiallyCorrectOutputsFail() throws Exception {
        String token = register("partial_user");
        UUID contestId = seedProblem("partial_user", List.of(new TestCase("1 2", "3"), new TestCase("4 5", "9")));

        submit(token, contestId, 1, List.of("3", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passedTestCaseCount").value(1))
                .andExpect(jsonPath("$.verdict").value("FAIL"));
    }

    @Test
    void wrongOutputCountIsRejected() throws Exception {
        String token = register("count_user");
        UUID contestId = seedProblem("count_user", List.of(new TestCase("1 2", "3"), new TestCase("4 5", "9")));

        submit(token, contestId, 1, List.of("3"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingProblemReturnsNotFound() throws Exception {
        String token = register("missing_user");

        submit(token, UUID.randomUUID(), 1, List.of("3"))
                .andExpect(status().isNotFound());
    }

    @Test
    void rapidResubmissionIsRateLimited() throws Exception {
        String token = register("ratelimit_user");
        UUID contestId = seedProblem("ratelimit_user", List.of(new TestCase("1 2", "3")));

        submit(token, contestId, 1, List.of("3")).andExpect(status().isOk());
        submit(token, contestId, 1, List.of("3")).andExpect(status().isTooManyRequests());
    }

    @Test
    void unregisteredUserCannotJudge() throws Exception {
        String token = register("unregistered_user");
        register("registered_user");
        UUID contestId = seedProblem("registered_user", List.of(new TestCase("1 2", "3")));

        submit(token, contestId, 1, List.of("3"))
                .andExpect(status().isForbidden());
    }

    private org.springframework.test.web.servlet.ResultActions submit(
            String token, UUID contestId, int problemNum, List<String> outputs) throws Exception {
        return mockMvc.perform(post("/contests/{contestId}/problems/{problemNum}/submissions", contestId, problemNum)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new SubmitRequest(outputs))));
    }

    private UUID seedProblem(String registeredUsername, List<TestCase> hiddenTestCases) throws Exception {
        Contest contest = new Contest();
        contest.setContestName("Test Contest");
        contest.setStartTime(LocalDateTime.now().minusHours(1));
        contest.setEndTime(LocalDateTime.now().plusHours(1));
        contest.setHostId(UUID.randomUUID());
        contest = contestRepository.save(contest);

        var registeredUser = userRepository.findByUsername(registeredUsername).orElseThrow();
        RegistrationId registrationId = new RegistrationId();
        registrationId.setContestId(contest.getContestId());
        registrationId.setUserId(registeredUser.getUserId());
        Registration registration = new Registration();
        registration.setRegId(registrationId);
        registration.setContest(contest);
        registration.setUser(registeredUser);
        registration.setRegistrationTime(LocalDateTime.now());
        registrationRepository.save(registration);

        Problem problem = new Problem();
        ProblemId problemId = new ProblemId();
        problemId.setContestId(contest.getContestId());
        problemId.setProblemNum(1);
        problem.setProblemId(problemId);
        problem.setContest(contest);
        problem.setTestCaseCount(hiddenTestCases.size());
        problemRepository.save(problem);

        fileStorageService.createContest(contest);
        fileStorageService.createProblemFiles(contest, problem, "statement");
        fileStorageService.saveHiddenTestCases(contest, problem, hiddenTestCases);

        return contest.getContestId();
    }

    private String register(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RegisterRequest(username, "pw12345"))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asString();
    }
}
