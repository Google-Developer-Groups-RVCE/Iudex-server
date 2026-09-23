package gdgrvce.iudex.server.controller;

import gdgrvce.iudex.server.dto.ContestRequest;
import gdgrvce.iudex.server.dto.ProblemRequest;
import gdgrvce.iudex.server.dto.TestCaseData;
import gdgrvce.iudex.server.dto.TestCaseUploadRequest;
import gdgrvce.iudex.server.model.Role;
import gdgrvce.iudex.server.model.User;
import gdgrvce.iudex.server.repository.UserRepository;
import gdgrvce.iudex.server.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Exercises the contest, problem, and registration endpoints end to end. */
@SpringBootTest
@AutoConfigureMockMvc
class ContestApiIntegrationTest {

    private static final String SECRET_OUTPUT = "the-hidden-answer";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    // ---------- contest CRUD ----------

    @Test
    void contestmasterCreatesReadsUpdatesAndDeletesAContest() throws Exception {
        String token = tokenFor(Role.CONTESTMASTER);
        String contestId = createContest(token, "Autumn Cup", plusHours(1), plusHours(3));

        mockMvc.perform(get("/api/contests/" + contestId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contestName").value("Autumn Cup"))
                .andExpect(jsonPath("$.status").value("UPCOMING"));

        mockMvc.perform(patch("/api/contests/" + contestId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new ContestRequest("Renamed Cup", null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contestName").value("Renamed Cup"));

        mockMvc.perform(delete("/api/contests/" + contestId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/contests/" + contestId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void contestantCannotCreateAContest() throws Exception {
        mockMvc.perform(post("/api/contests")
                        .header("Authorization", "Bearer " + tokenFor(Role.CONTESTANT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new ContestRequest("Nope", plusHours(1), plusHours(2)))))
                .andExpect(status().isForbidden());
    }

    @Test
    void anotherContestmasterCannotChangeSomeoneElsesContest() throws Exception {
        String owner = tokenFor(Role.CONTESTMASTER);
        String stranger = tokenFor(Role.CONTESTMASTER);
        String contestId = createContest(owner, "Owned Cup", plusHours(1), plusHours(3));

        mockMvc.perform(patch("/api/contests/" + contestId)
                        .header("Authorization", "Bearer " + stranger)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new ContestRequest("Hijacked", null, null))))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/contests/" + contestId).header("Authorization", "Bearer " + stranger))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsAContestThatEndsBeforeItStarts() throws Exception {
        mockMvc.perform(post("/api/contests")
                        .header("Authorization", "Bearer " + tokenFor(Role.CONTESTMASTER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new ContestRequest("Backwards", plusHours(3), plusHours(1)))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void requiresATokenForEveryContestRoute() throws Exception {
        mockMvc.perform(get("/api/contests")).andExpect(status().isUnauthorized());
    }

    // ---------- problems ----------

    @Test
    void problemsCannotBeAddedOnceTheContestHasStarted() throws Exception {
        String token = tokenFor(Role.CONTESTMASTER);
        String contestId = createContest(token, "Already Running", plusHours(-1), plusHours(2));

        mockMvc.perform(post("/api/contests/" + contestId + "/problems")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new ProblemRequest("Too Late", "stmt", null, null, null, null))))
                .andExpect(status().isConflict());
    }

    @Test
    void contestantCannotReadProblemsBeforeTheContestStarts() throws Exception {
        String owner = tokenFor(Role.CONTESTMASTER);
        String contestant = tokenFor(Role.CONTESTANT);
        String contestId = createContest(owner, "Not Yet", plusHours(1), plusHours(3));
        addProblem(owner, contestId, "Hidden Until Start");
        register(contestant, contestId);

        mockMvc.perform(get("/api/contests/" + contestId + "/problems")
                        .header("Authorization", "Bearer " + contestant))
                .andExpect(status().isConflict());
    }

    @Test
    void unregisteredContestantCannotReadProblems() throws Exception {
        String owner = tokenFor(Role.CONTESTMASTER);
        String outsider = tokenFor(Role.CONTESTANT);
        String contestId = createContest(owner, "Members Only", plusHours(1), plusHours(3));
        addProblem(owner, contestId, "Members Only Problem");
        startContestNow(owner, contestId);

        mockMvc.perform(get("/api/contests/" + contestId + "/problems")
                        .header("Authorization", "Bearer " + outsider))
                .andExpect(status().isForbidden());
    }

    @Test
    void contestmasterCanReadProblemsBeforeTheContestStarts() throws Exception {
        String owner = tokenFor(Role.CONTESTMASTER);
        String contestId = createContest(owner, "Preview Cup", plusHours(1), plusHours(3));
        addProblem(owner, contestId, "Draft Problem");

        mockMvc.perform(get("/api/contests/" + contestId + "/problems")
                        .header("Authorization", "Bearer " + owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Draft Problem"))
                .andExpect(jsonPath("$[0].problemNum").value(1));
    }

    @Test
    void problemsAreNumberedInTheOrderTheyAreAdded() throws Exception {
        String owner = tokenFor(Role.CONTESTMASTER);
        String contestId = createContest(owner, "Ordered Cup", plusHours(1), plusHours(3));
        addProblem(owner, contestId, "First");
        addProblem(owner, contestId, "Second");

        mockMvc.perform(get("/api/contests/" + contestId + "/problems")
                        .header("Authorization", "Bearer " + owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].problemNum").value(1))
                .andExpect(jsonPath("$[1].problemNum").value(2));
    }

    @Test
    void updatesAndDeletesAProblem() throws Exception {
        String owner = tokenFor(Role.CONTESTMASTER);
        String contestId = createContest(owner, "Edit Cup", plusHours(1), plusHours(3));
        String problemId = addProblem(owner, contestId, "Original");

        mockMvc.perform(patch("/api/problems/" + problemId)
                        .header("Authorization", "Bearer " + owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new ProblemRequest("Renamed", null, null, 2000, null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Renamed"))
                .andExpect(jsonPath("$.timeLimitMs").value(2000));

        mockMvc.perform(delete("/api/problems/" + problemId).header("Authorization", "Bearer " + owner))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/problems/" + problemId).header("Authorization", "Bearer " + owner))
                .andExpect(status().isNotFound());
    }

    // ---------- test data confidentiality ----------

    @Test
    void testEndpointReturnsInputsAndNeverExpectedOutput() throws Exception {
        String owner = tokenFor(Role.CONTESTMASTER);
        String contestant = tokenFor(Role.CONTESTANT);
        String contestId = createContest(owner, "Confidential Cup", plusHours(1), plusHours(3));
        String problemId = addProblem(owner, contestId, "Guarded");
        uploadTestCases(owner, problemId);
        register(contestant, contestId);
        startContestNow(owner, contestId);

        MvcResult result = mockMvc.perform(get("/api/problems/" + problemId + "/tests")
                        .header("Authorization", "Bearer " + contestant))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].inputData").value("sample in"))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertTrue(body.contains("hidden in"), "hidden inputs should be delivered to the client");
        assertFalse(body.contains(SECRET_OUTPUT), "expected output must never reach a contestant");
        assertFalse(body.contains("sample out"), "no output field belongs in this response");
    }

    @Test
    void problemDetailHidesHiddenCasesButShowsSamples() throws Exception {
        String owner = tokenFor(Role.CONTESTMASTER);
        String contestant = tokenFor(Role.CONTESTANT);
        String contestId = createContest(owner, "Sample Cup", plusHours(1), plusHours(3));
        String problemId = addProblem(owner, contestId, "Sampled");
        uploadTestCases(owner, problemId);
        register(contestant, contestId);
        startContestNow(owner, contestId);

        MvcResult result = mockMvc.perform(get("/api/problems/" + problemId)
                        .header("Authorization", "Bearer " + contestant))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.testCaseCount").value(2))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertTrue(body.contains("sample out"), "sample cases are public and may show their output");
        assertFalse(body.contains(SECRET_OUTPUT), "hidden expected output must not appear");
    }

    @Test
    void rejectsATestCaseMissingItsExpectedOutput() throws Exception {
        String owner = tokenFor(Role.CONTESTMASTER);
        String contestId = createContest(owner, "Invalid Data Cup", plusHours(1), plusHours(3));
        String problemId = addProblem(owner, contestId, "Needs Outputs");

        mockMvc.perform(put("/api/problems/" + problemId + "/testcases")
                        .header("Authorization", "Bearer " + owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new TestCaseUploadRequest(
                                List.of(new TestCaseData("in", null)), List.of()))))
                .andExpect(status().isBadRequest());
    }

    // ---------- registration ----------

    @Test
    void contestantRegistersAndAppearsInTheParticipantList() throws Exception {
        String owner = tokenFor(Role.CONTESTMASTER);
        String contestant = tokenFor(Role.CONTESTANT);
        String contestId = createContest(owner, "Open Cup", plusHours(1), plusHours(3));

        register(contestant, contestId);

        mockMvc.perform(get("/api/contests/" + contestId + "/registrations")
                        .header("Authorization", "Bearer " + owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void registeringTwiceIsRejected() throws Exception {
        String owner = tokenFor(Role.CONTESTMASTER);
        String contestant = tokenFor(Role.CONTESTANT);
        String contestId = createContest(owner, "Double Cup", plusHours(1), plusHours(3));

        register(contestant, contestId);
        mockMvc.perform(post("/api/contests/" + contestId + "/registrations")
                        .header("Authorization", "Bearer " + contestant))
                .andExpect(status().isConflict());
    }

    @Test
    void contestmasterCannotRegisterForTheirOwnContest() throws Exception {
        String owner = tokenFor(Role.CONTESTMASTER);
        String contestId = createContest(owner, "Own Cup", plusHours(1), plusHours(3));

        mockMvc.perform(post("/api/contests/" + contestId + "/registrations")
                        .header("Authorization", "Bearer " + owner))
                .andExpect(status().isForbidden());
    }

    @Test
    void registrationClosesOnceTheContestHasEnded() throws Exception {
        String owner = tokenFor(Role.CONTESTMASTER);
        String contestant = tokenFor(Role.CONTESTANT);
        String contestId = createContest(owner, "Finished Cup", plusHours(-3), plusHours(-1));

        mockMvc.perform(post("/api/contests/" + contestId + "/registrations")
                        .header("Authorization", "Bearer " + contestant))
                .andExpect(status().isConflict());
    }

    @Test
    void registrationStaysOpenAfterTheContestStarts() throws Exception {
        String owner = tokenFor(Role.CONTESTMASTER);
        String contestant = tokenFor(Role.CONTESTANT);
        String contestId = createContest(owner, "Late Join Cup", plusHours(-1), plusHours(2));

        mockMvc.perform(post("/api/contests/" + contestId + "/registrations")
                        .header("Authorization", "Bearer " + contestant))
                .andExpect(status().isCreated());
    }

    @Test
    void aParticipantCanRemoveThemselves() throws Exception {
        String owner = tokenFor(Role.CONTESTMASTER);
        User contestant = saveUser(Role.CONTESTANT);
        String token = jwtService.generateToken(contestant);
        String contestId = createContest(owner, "Leave Cup", plusHours(1), plusHours(3));
        register(token, contestId);

        mockMvc.perform(delete("/api/contests/" + contestId + "/registrations/" + contestant.getUserId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    void theContestmasterCanRemoveAParticipant() throws Exception {
        String owner = tokenFor(Role.CONTESTMASTER);
        User contestant = saveUser(Role.CONTESTANT);
        String contestId = createContest(owner, "Removal Cup", plusHours(1), plusHours(3));
        register(jwtService.generateToken(contestant), contestId);

        mockMvc.perform(delete("/api/contests/" + contestId + "/registrations/" + contestant.getUserId())
                        .header("Authorization", "Bearer " + owner))
                .andExpect(status().isNoContent());
    }

    @Test
    void anUnrelatedContestantCannotRemoveAnotherParticipant() throws Exception {
        String owner = tokenFor(Role.CONTESTMASTER);
        User contestant = saveUser(Role.CONTESTANT);
        String outsider = tokenFor(Role.CONTESTANT);
        String contestId = createContest(owner, "Meddling Cup", plusHours(1), plusHours(3));
        register(jwtService.generateToken(contestant), contestId);

        mockMvc.perform(delete("/api/contests/" + contestId + "/registrations/" + contestant.getUserId())
                        .header("Authorization", "Bearer " + outsider))
                .andExpect(status().isForbidden());
    }

    // ---------- helpers ----------

    private User saveUser(Role role) {
        User user = new User();
        user.setUsername("user_" + UUID.randomUUID());
        user.setPasswordHash(passwordEncoder.encode("pw12345"));
        user.setRole(role);
        return userRepository.save(user);
    }

    private String tokenFor(Role role) {
        return jwtService.generateToken(saveUser(role));
    }

    private OffsetDateTime plusHours(int hours) {
        return OffsetDateTime.now().plusHours(hours);
    }

    private String createContest(String token, String name, OffsetDateTime start, OffsetDateTime end)
            throws Exception {
        MvcResult result = mockMvc.perform(post("/api/contests")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new ContestRequest(name, start, end))))
                .andExpect(status().isCreated())
                .andReturn();
        return field(result, "contestId");
    }

    private String addProblem(String token, String contestId, String title) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/contests/" + contestId + "/problems")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new ProblemRequest(title, "statement body", "template body",
                                null, null, null))))
                .andExpect(status().isCreated())
                .andReturn();
        return field(result, "problemId");
    }

    private void uploadTestCases(String token, String problemId) throws Exception {
        mockMvc.perform(put("/api/problems/" + problemId + "/testcases")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new TestCaseUploadRequest(
                                List.of(new TestCaseData("sample in", "sample out")),
                                List.of(new TestCaseData("hidden in", SECRET_OUTPUT))))))
                .andExpect(status().isOk());
    }

    private void register(String token, String contestId) throws Exception {
        mockMvc.perform(post("/api/contests/" + contestId + "/registrations")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated());
    }

    /** Moves the start time into the past so contestants may read problems. */
    private void startContestNow(String token, String contestId) throws Exception {
        mockMvc.perform(patch("/api/contests/" + contestId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new ContestRequest(null, plusHours(-1), null))))
                .andExpect(status().isOk());
    }

    private String field(MvcResult result, String name) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get(name).asString();
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
