package gdgrvce.iudex.server.controller;

import gdgrvce.iudex.server.dto.ContestRequest;
import gdgrvce.iudex.server.dto.ProblemRequest;
import gdgrvce.iudex.server.dto.SubmissionRequest;
import gdgrvce.iudex.server.dto.SubmissionResult;
import gdgrvce.iudex.server.dto.TestCaseData;
import gdgrvce.iudex.server.dto.TestCaseUploadRequest;
import gdgrvce.iudex.server.model.Role;
import gdgrvce.iudex.server.model.User;
import gdgrvce.iudex.server.repository.UserRepository;
import gdgrvce.iudex.server.security.JwtService;
import gdgrvce.iudex.server.security.TestCaseCipher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the submission endpoints the way the judging client does: fetch the
 * encrypted test cases, decrypt them, report what the program printed, and let
 * the server decide what passed.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SubmissionApiIntegrationTest {

    private static final String SAMPLE_IN = "sample in";
    private static final String SAMPLE_OUT = "sample out";
    private static final String HIDDEN_IN = "hidden in";
    private static final String HIDDEN_OUT = "the-hidden-answer";

    /** What a correct program prints for each input. */
    private static final Map<String, String> CORRECT = Map.of(
            SAMPLE_IN, SAMPLE_OUT,
            HIDDEN_IN, HIDDEN_OUT);

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

    @Autowired
    private TestCaseCipher cipher;

    // ---------- grading ----------

    @Test
    void gradesAFullyCorrectSubmissionAsPassingEveryTestCase() throws Exception {
        Fixture fixture = runningContest();

        mockMvc.perform(submit(fixture, correctAnswers(fixture), 42))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.passedTestCaseCount").value(2))
                .andExpect(jsonPath("$.testCaseCount").value(2))
                .andExpect(jsonPath("$.submissionNum").value(1))
                .andExpect(jsonPath("$.submissionId").isNotEmpty())
                .andExpect(jsonPath("$.receivedAt").isNotEmpty())
                .andExpect(jsonPath("$.clientDurationMs").value(42));
    }

    @Test
    void gradesAWhollyWrongSubmissionAsPassingNothing() throws Exception {
        Fixture fixture = runningContest();

        List<SubmissionResult> wrong = new ArrayList<>();
        fixture.testCases.forEach((id, input) -> wrong.add(new SubmissionResult(id, cipher.encrypt("nonsense"))));

        mockMvc.perform(submit(fixture, wrong, null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.passedTestCaseCount").value(0));
    }

    @Test
    void scoresEachTestCaseIndependently() throws Exception {
        Fixture fixture = runningContest();

        // Right on the sample, wrong on the hidden case.
        List<SubmissionResult> partial = new ArrayList<>();
        fixture.testCases.forEach((id, input) -> partial.add(new SubmissionResult(
                id, cipher.encrypt(SAMPLE_IN.equals(input) ? SAMPLE_OUT : "wrong"))));

        mockMvc.perform(submit(fixture, partial, null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.passedTestCaseCount").value(1));
    }

    @Test
    void ignoresTrailingWhitespaceAndLineEndingStyle() throws Exception {
        Fixture fixture = runningContest();

        List<SubmissionResult> sloppy = new ArrayList<>();
        fixture.testCases.forEach((id, input) -> sloppy.add(new SubmissionResult(
                id, cipher.encrypt(CORRECT.get(input) + "   \r\n\r\n"))));

        mockMvc.perform(submit(fixture, sloppy, null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.passedTestCaseCount").value(2));
    }

    @Test
    void leadingWhitespaceStillCounts() throws Exception {
        Fixture fixture = runningContest();

        List<SubmissionResult> shifted = new ArrayList<>();
        fixture.testCases.forEach((id, input) -> shifted.add(new SubmissionResult(
                id, cipher.encrypt("   " + CORRECT.get(input)))));

        // Trailing whitespace is noise; leading whitespace is part of the answer.
        mockMvc.perform(submit(fixture, shifted, null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.passedTestCaseCount").value(0));
    }

    @Test
    void missingResultsScoreZeroRatherThanFailingTheRequest() throws Exception {
        Fixture fixture = runningContest();

        mockMvc.perform(submit(fixture, List.of(), null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.passedTestCaseCount").value(0));
    }

    @Test
    void countsARepeatedTestCaseOnlyOnce() throws Exception {
        Fixture fixture = runningContest();
        UUID sampleId = idOf(fixture, SAMPLE_IN);

        // The first answer for a test case stands, so sending it again wrong
        // then right cannot lift the score.
        List<SubmissionResult> repeated = List.of(
                new SubmissionResult(sampleId, cipher.encrypt("wrong")),
                new SubmissionResult(sampleId, cipher.encrypt(SAMPLE_OUT)));

        mockMvc.perform(submit(fixture, repeated, null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.passedTestCaseCount").value(0));
    }

    @Test
    void ignoresAnOutputForATestCaseTheProblemDoesNotHave() throws Exception {
        Fixture fixture = runningContest();

        List<SubmissionResult> results = new ArrayList<>(correctAnswers(fixture));
        results.add(new SubmissionResult(UUID.randomUUID(), cipher.encrypt("invented")));

        mockMvc.perform(submit(fixture, results, null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.passedTestCaseCount").value(2));
    }

    // ---------- what the client is not trusted with ----------

    @Test
    void rejectsAnOutputThatIsNotEncryptedUnderTheSharedKey() throws Exception {
        Fixture fixture = runningContest();

        List<SubmissionResult> plaintext = new ArrayList<>();
        fixture.testCases.forEach((id, input) -> plaintext.add(new SubmissionResult(id, CORRECT.get(input))));

        mockMvc.perform(submit(fixture, plaintext, null))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsAResultMissingItsTestCaseId() throws Exception {
        Fixture fixture = runningContest();

        mockMvc.perform(submit(fixture, List.of(new SubmissionResult(null, cipher.encrypt(SAMPLE_OUT))), null))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsAResultMissingItsOutput() throws Exception {
        Fixture fixture = runningContest();

        mockMvc.perform(submit(fixture, List.of(new SubmissionResult(idOf(fixture, SAMPLE_IN), null)), null))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsASubmissionWithNoProblemId() throws Exception {
        Fixture fixture = runningContest();

        mockMvc.perform(post("/api/submissions")
                        .header("Authorization", "Bearer " + fixture.contestant)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new SubmissionRequest(null, List.of(), null))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsANegativeClientDuration() throws Exception {
        Fixture fixture = runningContest();

        mockMvc.perform(submit(fixture, correctAnswers(fixture), -1))
                .andExpect(status().isBadRequest());
    }

    @Test
    void clientDurationIsRecordedButDoesNotAffectTheScore() throws Exception {
        Fixture fixture = runningContest();

        // An absurd self-reported runtime changes nothing about the verdict.
        mockMvc.perform(submit(fixture, correctAnswers(fixture), 0))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.clientDurationMs").value(0))
                .andExpect(jsonPath("$.passedTestCaseCount").value(2));
    }

    @Test
    void responseNeverNamesWhichTestCasesFailedOrWhatWasExpected() throws Exception {
        Fixture fixture = runningContest();

        List<SubmissionResult> partial = new ArrayList<>();
        fixture.testCases.forEach((id, input) -> partial.add(new SubmissionResult(
                id, cipher.encrypt(SAMPLE_IN.equals(input) ? SAMPLE_OUT : "wrong"))));

        MvcResult result = mockMvc.perform(submit(fixture, partial, null))
                .andExpect(status().isCreated())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertFalse(body.contains(HIDDEN_OUT), "expected output must never reach a contestant");
        assertFalse(body.contains(HIDDEN_IN), "hidden input must not be echoed back");
        for (UUID testCaseId : fixture.testCases.keySet()) {
            assertFalse(body.contains(testCaseId.toString()),
                    "naming a test case would describe the shape of the hidden data");
        }
    }

    // ---------- who may submit, and when ----------

    @Test
    void rejectsASubmissionBeforeTheContestStarts() throws Exception {
        Fixture fixture = preparedContest();
        register(fixture.contestant, fixture.contestId);

        mockMvc.perform(submit(fixture, List.of(), null))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsASubmissionAfterTheContestEnds() throws Exception {
        Fixture fixture = runningContest();
        moveWindow(fixture, -3, -1);

        mockMvc.perform(submit(fixture, correctAnswers(fixture), null))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsASubmissionFromSomeoneWhoNeverRegistered() throws Exception {
        Fixture fixture = runningContest();
        String outsider = tokenFor(Role.CONTESTANT);

        mockMvc.perform(post("/api/submissions")
                        .header("Authorization", "Bearer " + outsider)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new SubmissionRequest(fixture.problemId, List.of(), null))))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsASubmissionFromTheContestmasterWhoHoldsTheAnswers() throws Exception {
        Fixture fixture = runningContest();

        mockMvc.perform(post("/api/submissions")
                        .header("Authorization", "Bearer " + fixture.owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new SubmissionRequest(fixture.problemId, List.of(), null))))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsASubmissionWithoutAToken() throws Exception {
        Fixture fixture = runningContest();

        mockMvc.perform(post("/api/submissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new SubmissionRequest(fixture.problemId, List.of(), null))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsASubmissionForAnUnknownProblem() throws Exception {
        Fixture fixture = runningContest();

        mockMvc.perform(post("/api/submissions")
                        .header("Authorization", "Bearer " + fixture.contestant)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new SubmissionRequest(UUID.randomUUID(), List.of(), null))))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsASubmissionForAProblemWithNoTestCases() throws Exception {
        String owner = tokenFor(Role.CONTESTMASTER);
        String contestant = tokenFor(Role.CONTESTANT);
        String contestId = createContest(owner, "Empty Cup", plusHours(1), plusHours(3));
        String problemId = addProblem(owner, contestId, "No Data");
        register(contestant, contestId);
        startContestNow(owner, contestId);

        mockMvc.perform(post("/api/submissions")
                        .header("Authorization", "Bearer " + contestant)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new SubmissionRequest(UUID.fromString(problemId), List.of(), null))))
                .andExpect(status().isConflict());
    }

    // ---------- attempt history ----------

    @Test
    void numbersAttemptsPerContestantPerProblem() throws Exception {
        Fixture fixture = runningContest();

        mockMvc.perform(submit(fixture, List.of(), null))
                .andExpect(jsonPath("$.submissionNum").value(1));
        mockMvc.perform(submit(fixture, List.of(), null))
                .andExpect(jsonPath("$.submissionNum").value(2));
        mockMvc.perform(submit(fixture, correctAnswers(fixture), null))
                .andExpect(jsonPath("$.submissionNum").value(3))
                .andExpect(jsonPath("$.passedTestCaseCount").value(2));
    }

    @Test
    void twoContestantsNumberTheirAttemptsIndependently() throws Exception {
        Fixture fixture = runningContest();
        String second = tokenFor(Role.CONTESTANT);
        register(second, fixture.contestId);

        mockMvc.perform(submit(fixture, List.of(), null))
                .andExpect(jsonPath("$.submissionNum").value(1));
        mockMvc.perform(post("/api/submissions")
                        .header("Authorization", "Bearer " + second)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new SubmissionRequest(fixture.problemId, List.of(), null))))
                .andExpect(jsonPath("$.submissionNum").value(1));
    }

    @Test
    void contestantReadsBackTheirOwnAttemptHistory() throws Exception {
        Fixture fixture = runningContest();
        mockMvc.perform(submit(fixture, List.of(), null)).andExpect(status().isCreated());
        mockMvc.perform(submit(fixture, correctAnswers(fixture), null)).andExpect(status().isCreated());

        mockMvc.perform(get("/api/problems/" + fixture.problemId + "/submissions")
                        .header("Authorization", "Bearer " + fixture.contestant))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].passedTestCaseCount").value(0))
                .andExpect(jsonPath("$[1].passedTestCaseCount").value(2));
    }

    @Test
    void attemptHistoryShowsOnlyTheCallersOwnSubmissions() throws Exception {
        Fixture fixture = runningContest();
        String other = tokenFor(Role.CONTESTANT);
        register(other, fixture.contestId);

        mockMvc.perform(submit(fixture, List.of(), null)).andExpect(status().isCreated());

        mockMvc.perform(get("/api/problems/" + fixture.problemId + "/submissions")
                        .header("Authorization", "Bearer " + other))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void contestantReadsTheirOwnSubmissionById() throws Exception {
        Fixture fixture = runningContest();
        String submissionId = submitAndReturnId(fixture);

        mockMvc.perform(get("/api/submissions/" + submissionId)
                        .header("Authorization", "Bearer " + fixture.contestant))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passedTestCaseCount").value(2));
    }

    @Test
    void contestantCannotReadAnotherContestantsSubmission() throws Exception {
        Fixture fixture = runningContest();
        String submissionId = submitAndReturnId(fixture);
        String nosy = tokenFor(Role.CONTESTANT);
        register(nosy, fixture.contestId);

        mockMvc.perform(get("/api/submissions/" + submissionId)
                        .header("Authorization", "Bearer " + nosy))
                .andExpect(status().isForbidden());
    }

    @Test
    void contestmasterReadsAnyOfTheirContestsSubmissions() throws Exception {
        Fixture fixture = runningContest();
        String submissionId = submitAndReturnId(fixture);

        mockMvc.perform(get("/api/submissions/" + submissionId)
                        .header("Authorization", "Bearer " + fixture.owner))
                .andExpect(status().isOk());
    }

    @Test
    void unknownSubmissionIsNotFound() throws Exception {
        String contestant = tokenFor(Role.CONTESTANT);

        mockMvc.perform(get("/api/submissions/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + contestant))
                .andExpect(status().isNotFound());
    }

    @Test
    void contestmasterSeesEveryAttemptInTheContest() throws Exception {
        Fixture fixture = runningContest();
        String second = tokenFor(Role.CONTESTANT);
        register(second, fixture.contestId);

        mockMvc.perform(submit(fixture, List.of(), null)).andExpect(status().isCreated());
        mockMvc.perform(post("/api/submissions")
                        .header("Authorization", "Bearer " + second)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new SubmissionRequest(fixture.problemId, List.of(), null))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/contests/" + fixture.contestId + "/submissions")
                        .header("Authorization", "Bearer " + fixture.owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void contestantCannotListEveryAttemptInTheContest() throws Exception {
        Fixture fixture = runningContest();

        mockMvc.perform(get("/api/contests/" + fixture.contestId + "/submissions")
                        .header("Authorization", "Bearer " + fixture.contestant))
                .andExpect(status().isForbidden());
    }

    @Test
    void leavingTheContestDiscardsTheAttemptHistory() throws Exception {
        Fixture fixture = runningContest();
        mockMvc.perform(submit(fixture, correctAnswers(fixture), null)).andExpect(status().isCreated());

        User contestant = userRepository.findByUsername(usernameOf(fixture.contestant)).orElseThrow();
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/contests/" + fixture.contestId + "/registrations/" + contestant.getUserId())
                        .header("Authorization", "Bearer " + fixture.contestant))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/contests/" + fixture.contestId + "/submissions")
                        .header("Authorization", "Bearer " + fixture.owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ---------- helpers ----------

    /** A contest, a problem with two test cases, and a registered contestant. */
    private record Fixture(String owner, String contestant, String contestId, UUID problemId,
                           Map<UUID, String> testCases) {
    }

    /** Everything set up, but the contest has not opened yet. */
    private Fixture preparedContest() throws Exception {
        String owner = tokenFor(Role.CONTESTMASTER);
        String contestant = tokenFor(Role.CONTESTANT);
        String contestId = createContest(owner, "Judged Cup " + UUID.randomUUID(), plusHours(1), plusHours(3));
        String problemId = addProblem(owner, contestId, "Graded");
        uploadTestCases(owner, problemId);
        return new Fixture(owner, contestant, contestId, UUID.fromString(problemId), Map.of());
    }

    /** A contest that is open, with the contestant registered and holding the test cases. */
    private Fixture runningContest() throws Exception {
        Fixture prepared = preparedContest();
        register(prepared.contestant, prepared.contestId);
        startContestNow(prepared.owner, prepared.contestId);
        return new Fixture(prepared.owner, prepared.contestant, prepared.contestId, prepared.problemId,
                fetchTestCases(prepared.contestant, prepared.problemId));
    }

    /** Fetches the encrypted test cases and decrypts them, exactly as the client does. */
    private Map<UUID, String> fetchTestCases(String token, UUID problemId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/problems/" + problemId + "/tests")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode cases = objectMapper.readTree(result.getResponse().getContentAsString());
        Map<UUID, String> decrypted = new LinkedHashMap<>();
        for (int index = 0; index < cases.size(); index++) {
            JsonNode testCase = cases.get(index);
            decrypted.put(UUID.fromString(testCase.get("id").asString()),
                    cipher.decrypt(testCase.get("encryptedInput").asString()));
        }
        assertEquals(2, decrypted.size(), "both the sample and the hidden case should be delivered");
        assertTrue(decrypted.containsValue(SAMPLE_IN));
        assertTrue(decrypted.containsValue(HIDDEN_IN));
        return decrypted;
    }

    /** What a correct program would report for every test case it was given. */
    private List<SubmissionResult> correctAnswers(Fixture fixture) {
        List<SubmissionResult> results = new ArrayList<>();
        fixture.testCases.forEach((id, input) ->
                results.add(new SubmissionResult(id, cipher.encrypt(CORRECT.get(input)))));
        return results;
    }

    private UUID idOf(Fixture fixture, String input) {
        return fixture.testCases.entrySet().stream()
                .filter(entry -> entry.getValue().equals(input))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElseThrow();
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder submit(
            Fixture fixture, List<SubmissionResult> results, Integer clientDurationMs) throws Exception {
        return post("/api/submissions")
                .header("Authorization", "Bearer " + fixture.contestant)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new SubmissionRequest(fixture.problemId, results, clientDurationMs)));
    }

    private String submitAndReturnId(Fixture fixture) throws Exception {
        MvcResult result = mockMvc.perform(submit(fixture, correctAnswers(fixture), null))
                .andExpect(status().isCreated())
                .andReturn();
        return field(result, "submissionId");
    }

    /** Moves the contest window, so a test can put it in the past. */
    private void moveWindow(Fixture fixture, int startHours, int endHours) throws Exception {
        mockMvc.perform(patch("/api/contests/" + fixture.contestId)
                        .header("Authorization", "Bearer " + fixture.owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new ContestRequest(null, plusHours(startHours), plusHours(endHours)))))
                .andExpect(status().isOk());
    }

    private User saveUser(Role role) {
        User user = new User();
        user.setUsername("user_" + UUID.randomUUID());
        user.setPasswordHash(passwordEncoder.encode("pw123456"));
        user.setRole(role);
        return userRepository.save(user);
    }

    private String tokenFor(Role role) {
        return jwtService.generateToken(saveUser(role));
    }

    private String usernameOf(String token) {
        return jwtService.extractUsername(token);
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
                                List.of(new TestCaseData(SAMPLE_IN, SAMPLE_OUT)),
                                List.of(new TestCaseData(HIDDEN_IN, HIDDEN_OUT))))))
                .andExpect(status().isOk());
    }

    private void register(String token, String contestId) throws Exception {
        mockMvc.perform(post("/api/contests/" + contestId + "/registrations")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated());
    }

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
