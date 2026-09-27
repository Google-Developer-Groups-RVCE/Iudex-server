package gdgrvce.iudex.server.controller;

import gdgrvce.iudex.server.dto.ContestRequest;
import gdgrvce.iudex.server.dto.ProblemRequest;
import gdgrvce.iudex.server.dto.SubmissionRequest;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The submission rate limit sits behind authentication, the problem and contest
 * lookup, and the registration and contest-window rules, so only an attempt the
 * contest would otherwise accept spends a contestant's slot.
 */
@SpringBootTest(properties = "iudex.submission.min-interval-ms=60000")
@AutoConfigureMockMvc
class SubmissionRateLimitIntegrationTest {

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

    @Test
    void rejectsAnImmediateResubmissionWithRetryAfter() throws Exception {
        String owner = tokenFor(Role.CONTESTMASTER);
        String contestant = tokenFor(Role.CONTESTANT);
        String contestId = createContest(owner);
        UUID problemId = addProblemWithTestCases(owner, contestId);
        register(contestant, contestId);
        startContestNow(owner, contestId);

        mockMvc.perform(submit(contestant, problemId))
                .andExpect(status().isCreated());
        mockMvc.perform(submit(contestant, problemId))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(jsonPath("$.error").isNotEmpty());
    }

    @Test
    void anAttemptBeforeTheContestStartsDoesNotSpendTheSlot() throws Exception {
        String owner = tokenFor(Role.CONTESTMASTER);
        String contestant = tokenFor(Role.CONTESTANT);
        String contestId = createContest(owner);
        UUID problemId = addProblemWithTestCases(owner, contestId);
        register(contestant, contestId);

        mockMvc.perform(submit(contestant, problemId))
                .andExpect(status().isConflict());

        startContestNow(owner, contestId);
        mockMvc.perform(submit(contestant, problemId))
                .andExpect(status().isCreated());
    }

    @Test
    void refusedSubmissionsKeepTheirOwnStatusRatherThanBeingThrottled() throws Exception {
        String owner = tokenFor(Role.CONTESTMASTER);
        String outsider = tokenFor(Role.CONTESTANT);
        String contestId = createContest(owner);
        UUID problemId = addProblemWithTestCases(owner, contestId);
        startContestNow(owner, contestId);

        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(submit(outsider, problemId)).andExpect(status().isForbidden());
            mockMvc.perform(submit(owner, problemId)).andExpect(status().isForbidden());
            mockMvc.perform(submit(outsider, UUID.randomUUID())).andExpect(status().isNotFound());
        }
    }

    // ---------- helpers ----------

    private MockHttpServletRequestBuilder submit(String token, UUID problemId) throws Exception {
        return post("/api/submissions")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new SubmissionRequest(problemId, List.of(), null)));
    }

    private String tokenFor(Role role) {
        User user = new User();
        user.setUsername("user_" + UUID.randomUUID());
        user.setPasswordHash(passwordEncoder.encode("pw123456"));
        user.setRole(role);
        return jwtService.generateToken(userRepository.save(user));
    }

    private String createContest(String token) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/contests")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new ContestRequest("Rate Cup",
                                OffsetDateTime.now().plusHours(1), OffsetDateTime.now().plusHours(3)))))
                .andExpect(status().isCreated())
                .andReturn();
        return field(result, "contestId");
    }

    private UUID addProblemWithTestCases(String token, String contestId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/contests/" + contestId + "/problems")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new ProblemRequest("Limits", "statement body", "template body",
                                null, null, null))))
                .andExpect(status().isCreated())
                .andReturn();
        String problemId = field(result, "problemId");

        mockMvc.perform(put("/api/problems/" + problemId + "/testcases")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new TestCaseUploadRequest(
                                List.of(new TestCaseData("in", "out")),
                                List.of(new TestCaseData("hidden in", "hidden out"))))))
                .andExpect(status().isOk());
        return UUID.fromString(problemId);
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
                        .content(json(new ContestRequest(null, OffsetDateTime.now().minusHours(1), null))))
                .andExpect(status().isOk());
    }

    private String field(MvcResult result, String name) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get(name).asString();
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
