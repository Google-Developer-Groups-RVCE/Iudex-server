package gdgrvce.iudex.server.controller;

import gdgrvce.iudex.server.dto.ContestRequest;
import gdgrvce.iudex.server.dto.LoginRequest;
import gdgrvce.iudex.server.dto.RegisterRequest;
import gdgrvce.iudex.server.dto.RoleUpdateRequest;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers the path from a bare deployment to a working contest: the bootstrap
 * administrator exists, and promotes somebody to contestmaster.
 *
 * <p>Registration only ever produces a contestant, so without this path no
 * contest could be created and every contest endpoint would be unreachable.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
class UserAdminApiIntegrationTest {

    private static final String BOOTSTRAP_USERNAME = "bootstrap_admin";
    private static final String BOOTSTRAP_PASSWORD = "bootstrap_pw12345";

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

    // ---------- the bootstrap administrator ----------

    @Test
    void startupCreatesTheConfiguredAdministrator() {
        User admin = userRepository.findByUsername(BOOTSTRAP_USERNAME).orElseThrow(
                () -> new AssertionError("the bootstrap administrator should exist"));
        assertEquals(Role.ADMIN, admin.getRole());
    }

    @Test
    void theBootstrapAdministratorCanLogInWithTheConfiguredPassword() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginRequest(BOOTSTRAP_USERNAME, BOOTSTRAP_PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void theBootstrapPasswordIsStoredHashed() {
        User admin = userRepository.findByUsername(BOOTSTRAP_USERNAME).orElseThrow();
        assertFalse(admin.getPasswordHash().contains(BOOTSTRAP_PASSWORD));
        assertTrue(passwordEncoder.matches(BOOTSTRAP_PASSWORD, admin.getPasswordHash()));
    }

    // ---------- promotion, end to end ----------

    @Test
    void anAdministratorPromotesARegisteredUserIntoAWorkingContestmaster() throws Exception {
        // Someone signs up the ordinary way, and gets the ordinary role.
        String username = "promote_" + UUID.randomUUID();
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RegisterRequest(username, "pw123456"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("CONTESTANT"));

        UUID userId = userRepository.findByUsername(username).orElseThrow().getUserId();

        // As a contestant they cannot create a contest.
        String contestantToken = login(username, "pw123456");
        mockMvc.perform(createContest(contestantToken))
                .andExpect(status().isForbidden());

        // The administrator promotes them.
        mockMvc.perform(patch("/api/users/" + userId + "/role")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RoleUpdateRequest(Role.CONTESTMASTER))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("CONTESTMASTER"))
                .andExpect(jsonPath("$.username").value(username));

        // Now the same account can, and a fresh login reports the new role.
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginRequest(username, "pw123456"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("CONTESTMASTER"));
        mockMvc.perform(createContest(login(username, "pw123456")))
                .andExpect(status().isCreated());
    }

    @Test
    void promotionTakesEffectOnATokenIssuedBeforeIt() throws Exception {
        String username = "stale_" + UUID.randomUUID();
        register(username);
        String tokenFromBefore = login(username, "pw123456");
        UUID userId = userRepository.findByUsername(username).orElseThrow().getUserId();

        promote(userId, Role.CONTESTMASTER);

        // The role is read from the database on each request, not from the token.
        mockMvc.perform(createContest(tokenFromBefore))
                .andExpect(status().isCreated());
    }

    @Test
    void anAdministratorCanDemoteSomeoneAgain() throws Exception {
        String username = "demote_" + UUID.randomUUID();
        register(username);
        UUID userId = userRepository.findByUsername(username).orElseThrow().getUserId();

        promote(userId, Role.CONTESTMASTER);
        promote(userId, Role.CONTESTANT);

        mockMvc.perform(createContest(login(username, "pw123456")))
                .andExpect(status().isForbidden());
    }

    // ---------- who may administer ----------

    @Test
    void administratorsListEveryAccount() throws Exception {
        mockMvc.perform(get("/api/users").header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").isNotEmpty())
                .andExpect(jsonPath("$[0].username").isNotEmpty())
                .andExpect(jsonPath("$[0].role").isNotEmpty());
    }

    @Test
    void theUserListingNeverCarriesPasswordMaterial() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/users")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertFalse(body.contains("passwordHash"), "password hashes must not be listed");
        assertFalse(body.contains("$2a$"), "no bcrypt hash should appear in the response");
    }

    @Test
    void aContestantCannotListAccounts() throws Exception {
        mockMvc.perform(get("/api/users").header("Authorization", "Bearer " + tokenFor(Role.CONTESTANT)))
                .andExpect(status().isForbidden());
    }

    @Test
    void aContestmasterCannotListAccounts() throws Exception {
        mockMvc.perform(get("/api/users").header("Authorization", "Bearer " + tokenFor(Role.CONTESTMASTER)))
                .andExpect(status().isForbidden());
    }

    @Test
    void aContestmasterCannotPromoteAnybody() throws Exception {
        String username = "target_" + UUID.randomUUID();
        register(username);
        UUID userId = userRepository.findByUsername(username).orElseThrow().getUserId();

        mockMvc.perform(patch("/api/users/" + userId + "/role")
                        .header("Authorization", "Bearer " + tokenFor(Role.CONTESTMASTER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RoleUpdateRequest(Role.ADMIN))))
                .andExpect(status().isForbidden());
    }

    @Test
    void aContestantCannotPromoteThemselves() throws Exception {
        String username = "selfserve_" + UUID.randomUUID();
        register(username);
        UUID userId = userRepository.findByUsername(username).orElseThrow().getUserId();

        mockMvc.perform(patch("/api/users/" + userId + "/role")
                        .header("Authorization", "Bearer " + login(username, "pw123456"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RoleUpdateRequest(Role.CONTESTMASTER))))
                .andExpect(status().isForbidden());
    }

    @Test
    void administrationRequiresAToken() throws Exception {
        mockMvc.perform(get("/api/users")).andExpect(status().isUnauthorized());
    }

    // ---------- guard rails ----------

    @Test
    void anAdministratorCannotChangeTheirOwnRole() throws Exception {
        // Otherwise the last administrator could lock everyone out of the deployment.
        UUID adminId = userRepository.findByUsername(BOOTSTRAP_USERNAME).orElseThrow().getUserId();

        mockMvc.perform(patch("/api/users/" + adminId + "/role")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RoleUpdateRequest(Role.CONTESTANT))))
                .andExpect(status().isForbidden());

        assertEquals(Role.ADMIN, userRepository.findByUsername(BOOTSTRAP_USERNAME).orElseThrow().getRole());
    }

    @Test
    void promotingAnUnknownUserIsNotFound() throws Exception {
        mockMvc.perform(patch("/api/users/" + UUID.randomUUID() + "/role")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RoleUpdateRequest(Role.CONTESTMASTER))))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsARoleChangeWithNoRole() throws Exception {
        String username = "norole_" + UUID.randomUUID();
        register(username);
        UUID userId = userRepository.findByUsername(username).orElseThrow().getUserId();

        mockMvc.perform(patch("/api/users/" + userId + "/role")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsARoleThatDoesNotExist() throws Exception {
        String username = "badrole_" + UUID.randomUUID();
        register(username);
        UUID userId = userRepository.findByUsername(username).orElseThrow().getUserId();

        mockMvc.perform(patch("/api/users/" + userId + "/role")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"SUPERUSER\"}"))
                .andExpect(status().isBadRequest());
    }

    // ---------- helpers ----------

    private String adminToken() {
        return jwtService.generateToken(userRepository.findByUsername(BOOTSTRAP_USERNAME).orElseThrow());
    }

    private void register(String username) throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RegisterRequest(username, "pw123456"))))
                .andExpect(status().isCreated());
    }

    private String login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginRequest(username, password))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asString();
    }

    private void promote(UUID userId, Role role) throws Exception {
        mockMvc.perform(patch("/api/users/" + userId + "/role")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RoleUpdateRequest(role))))
                .andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder createContest(String token)
            throws Exception {
        return post("/api/contests")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new ContestRequest("Promotion Cup " + UUID.randomUUID(),
                        OffsetDateTime.now().plusHours(1), OffsetDateTime.now().plusHours(3))));
    }

    private String tokenFor(Role role) {
        User user = new User();
        user.setUsername("user_" + UUID.randomUUID());
        user.setPasswordHash(passwordEncoder.encode("pw123456"));
        user.setRole(role);
        return jwtService.generateToken(userRepository.save(user));
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
