package gdgrvce.iudex.server.service;

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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A storage failure partway through a problem write must leave the database and
 * file storage agreeing with each other, as if the write had never happened.
 *
 * <p>Each test fails one file operation once, then lets it through, so the undo
 * that runs on rollback can use the same operation.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProblemStorageConsistencyIntegrationTest {

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

    @MockitoSpyBean
    private FileStorageService fileStorageService;

    @Value("${iudex.storage.root}")
    private String storageRoot;

    @Test
    void aFailedAddLeavesNoProblemRowAndNoFiles() throws Exception {
        String owner = tokenFor(Role.CONTESTMASTER);
        String contestId = createContest(owner);
        doThrow(new IOException("disk full")).doCallRealMethod()
                .when(fileStorageService).saveTemplateSolution(any(), any(), any());

        mockMvc.perform(post("/api/contests/" + contestId + "/problems")
                        .header("Authorization", "Bearer " + owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new ProblemRequest("Doomed", "statement", "template", null, null, null))))
                .andExpect(status().isInternalServerError());

        assertFalse(Files.exists(Path.of(storageRoot, contestId, "1")),
                "the rolled-back problem's directory should be gone");
        mockMvc.perform(get("/api/contests/" + contestId + "/problems").header("Authorization", "Bearer " + owner))
                .andExpect(jsonPath("$.length()").value(0));

        // The number is free again, with nothing left on disk to trip over.
        addProblem(owner, contestId, "Retry");
        mockMvc.perform(get(problemPath(contestId, 1)).header("Authorization", "Bearer " + owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Retry"));
    }

    @Test
    void aFailedUpdateLeavesTheOriginalFiles() throws Exception {
        String owner = tokenFor(Role.CONTESTMASTER);
        String contestId = createContest(owner);
        addProblem(owner, contestId, "Original");
        doThrow(new IOException("disk full")).doCallRealMethod()
                .when(fileStorageService).saveStatement(any(), any(), any());

        // Metadata is written before the statement, so this fails halfway.
        mockMvc.perform(patch(problemPath(contestId, 1))
                        .header("Authorization", "Bearer " + owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new ProblemRequest("Renamed", "new statement", null, 5000, null, null))))
                .andExpect(status().isInternalServerError());

        mockMvc.perform(get(problemPath(contestId, 1)).header("Authorization", "Bearer " + owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Original"))
                .andExpect(jsonPath("$.statement").value("statement body"))
                .andExpect(jsonPath("$.timeLimitMs").value(1000));
    }

    @Test
    void aFailedDeleteKeepsTheProblemAndItsFiles() throws Exception {
        String owner = tokenFor(Role.CONTESTMASTER);
        String contestId = createContest(owner);
        addProblem(owner, contestId, "Survivor");
        uploadTestCases(owner, contestId, "sample in");
        // Gets partway through the directory before failing.
        doAnswer(invocation -> {
            Files.delete(Path.of(storageRoot, contestId, "1", "statement.txt"));
            throw new IOException("disk full");
        }).doCallRealMethod().when(fileStorageService).deleteProblem(any(), any());

        mockMvc.perform(delete(problemPath(contestId, 1)).header("Authorization", "Bearer " + owner))
                .andExpect(status().isInternalServerError());

        mockMvc.perform(get(problemPath(contestId, 1)).header("Authorization", "Bearer " + owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Survivor"))
                .andExpect(jsonPath("$.statement").value("statement body"))
                .andExpect(jsonPath("$.testCaseCount").value(2))
                .andExpect(jsonPath("$.sampleTestCases[0].input").value("sample in"));
    }

    @Test
    void aFailedTestCaseReplacementKeepsTheOldCasesAndCount() throws Exception {
        String owner = tokenFor(Role.CONTESTMASTER);
        String contestId = createContest(owner);
        addProblem(owner, contestId, "Stable");
        uploadTestCases(owner, contestId, "old sample");
        doThrow(new IOException("disk full")).doCallRealMethod()
                .when(fileStorageService).saveHiddenTestCases(any(), any(), any());

        // Samples are written before hidden cases, so this fails halfway.
        mockMvc.perform(put(problemPath(contestId, 1) + "/testcases")
                        .header("Authorization", "Bearer " + owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new TestCaseUploadRequest(
                                List.of(new TestCaseData("new sample", "out"),
                                        new TestCaseData("another", "out")),
                                List.of(new TestCaseData("new hidden", "out"))))))
                .andExpect(status().isInternalServerError());

        mockMvc.perform(get(problemPath(contestId, 1)).header("Authorization", "Bearer " + owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.testCaseCount").value(2))
                .andExpect(jsonPath("$.sampleTestCases.length()").value(1))
                .andExpect(jsonPath("$.sampleTestCases[0].input").value("old sample"));
    }

    // ---------- failures at commit, after every file write succeeded ----------

    @Test
    void anAddThatFailsAtCommitLeavesNoProblemRowAndNoFiles() throws Exception {
        String owner = tokenFor(Role.CONTESTMASTER);
        String contestId = createContest(owner);
        // The template is the last file an add writes.
        doAnswer(invocation -> {
            invocation.callRealMethod();
            failTheCommit();
            return null;
        }).when(fileStorageService).saveTemplateSolution(any(), any(), any());

        mockMvc.perform(post("/api/contests/" + contestId + "/problems")
                        .header("Authorization", "Bearer " + owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new ProblemRequest("Doomed", "statement", "template", null, null, null))))
                .andExpect(status().isInternalServerError());

        assertFalse(Files.exists(Path.of(storageRoot, contestId, "1")),
                "files written before the failed commit should be removed");
        mockMvc.perform(get("/api/contests/" + contestId + "/problems").header("Authorization", "Bearer " + owner))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void aDeleteThatFailsAtCommitKeepsTheProblemAndItsFiles() throws Exception {
        String owner = tokenFor(Role.CONTESTMASTER);
        String contestId = createContest(owner);
        addProblem(owner, contestId, "Survivor");
        uploadTestCases(owner, contestId, "sample in");
        doAnswer(invocation -> {
            invocation.callRealMethod();
            failTheCommit();
            return null;
        }).when(fileStorageService).deleteProblem(any(), any());

        mockMvc.perform(delete(problemPath(contestId, 1)).header("Authorization", "Bearer " + owner))
                .andExpect(status().isInternalServerError());

        mockMvc.perform(get(problemPath(contestId, 1)).header("Authorization", "Bearer " + owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Survivor"))
                .andExpect(jsonPath("$.statement").value("statement body"))
                .andExpect(jsonPath("$.solutionTemplate").value("template body"))
                .andExpect(jsonPath("$.testCaseCount").value(2))
                .andExpect(jsonPath("$.sampleTestCases[0].input").value("sample in"));
    }

    /**
     * Makes the surrounding transaction fail as it commits, the way a lost
     * connection or a constraint checked only at commit would. Spring rolls
     * back when a before-commit callback throws.
     */
    private static void failTheCommit() {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void beforeCommit(boolean readOnly) {
                throw new IllegalStateException("simulated commit failure");
            }
        });
    }

    // ---------- helpers ----------

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
                        .content(json(new ContestRequest("Storage Cup " + UUID.randomUUID(),
                                OffsetDateTime.now().plusHours(1), OffsetDateTime.now().plusHours(3)))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("contestId").asString();
    }

    private void addProblem(String token, String contestId, String title) throws Exception {
        mockMvc.perform(post("/api/contests/" + contestId + "/problems")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new ProblemRequest(title, "statement body", "template body",
                                null, null, null))))
                .andExpect(status().isCreated());
    }

    private void uploadTestCases(String token, String contestId, String sampleInput) throws Exception {
        mockMvc.perform(put(problemPath(contestId, 1) + "/testcases")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new TestCaseUploadRequest(
                                List.of(new TestCaseData(sampleInput, "sample out")),
                                List.of(new TestCaseData("hidden in", "hidden out"))))))
                .andExpect(status().isOk());
    }

    private String problemPath(String contestId, int problemNum) {
        return "/api/contests/" + contestId + "/problems/" + problemNum;
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
