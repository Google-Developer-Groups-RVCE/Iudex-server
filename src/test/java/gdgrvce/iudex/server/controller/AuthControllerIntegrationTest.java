package gdgrvce.iudex.server.controller;

import gdgrvce.iudex.server.dto.LoginRequest;
import gdgrvce.iudex.server.dto.RegisterRequest;
import gdgrvce.iudex.server.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Checks the auth endpoints through the real security setup and test database. */
@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Test
    void registerReturnsCreatedWithToken() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RegisterRequest("reg_user", "pw123456"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.username").value("reg_user"))
                .andExpect(jsonPath("$.role").value("CONTESTANT"));
    }

    @Test
    void duplicateRegistrationReturnsConflict() throws Exception {
        RegisterRequest request = new RegisterRequest("dupe_user", "pw123456");
        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(json(request)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(json(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void loginWithValidCredentialsReturnsToken() throws Exception {
        register("login_user", "pw123456");
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginRequest("login_user", "pw123456"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void loginWithWrongPasswordReturnsUnauthorized() throws Exception {
        register("wrongpw_user", "pw123456");
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginRequest("wrongpw_user", "nope"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meWithoutTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meWithValidTokenReturnsCurrentUser() throws Exception {
        String token = register("me_user", "pw123456");
        mockMvc.perform(get("/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("me_user"))
                .andExpect(jsonPath("$.role").value("CONTESTANT"));
    }

    @Test
    void logoutWithoutTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/auth/logout"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesTheToken() throws Exception {
        String token = register("logout_user", "pw123456");
        mockMvc.perform(post("/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesEveryTokenForTheUser() throws Exception {
        String first = register("multi_device_user", "pw123456");
        String second = login("multi_device_user", "pw123456");
        mockMvc.perform(post("/auth/logout").header("Authorization", "Bearer " + second))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/auth/me").header("Authorization", "Bearer " + first))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginAfterLogoutIssuesAWorkingToken() throws Exception {
        String old = register("relogin_user", "pw123456");
        mockMvc.perform(post("/auth/logout").header("Authorization", "Bearer " + old))
                .andExpect(status().isNoContent());
        String fresh = login("relogin_user", "pw123456");
        mockMvc.perform(get("/auth/me").header("Authorization", "Bearer " + fresh))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("relogin_user"));
    }

    @Test
    void logoutDoesNotAffectOtherUsers() throws Exception {
        String leaving = register("leaving_user", "pw123456");
        String staying = register("staying_user", "pw123456");
        mockMvc.perform(post("/auth/logout").header("Authorization", "Bearer " + leaving))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/auth/me").header("Authorization", "Bearer " + staying))
                .andExpect(status().isOk());
    }

    @Test
    void logoutReturnsNoBody() throws Exception {
        String token = register("quiet_logout_user", "pw123456");
        mockMvc.perform(post("/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    @Test
    void logoutWithGarbageTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/auth/logout").header("Authorization", "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void revokedTokenCannotLogOutAgain() throws Exception {
        String token = register("double_logout_user", "pw123456");
        mockMvc.perform(post("/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    /** Revocation is enforced by the filter, so it covers the API, not just /auth. */
    @Test
    void revokedTokenIsRejectedByApiEndpoints() throws Exception {
        String token = register("api_logout_user", "pw123456");
        mockMvc.perform(get("/api/contests").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(post("/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/contests").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void eachLogoutAdvancesTheStoredVersion() throws Exception {
        String first = register("versioned_user", "pw123456");
        assertEquals(0, storedVersion("versioned_user"));

        mockMvc.perform(post("/auth/logout").header("Authorization", "Bearer " + first))
                .andExpect(status().isNoContent());
        assertEquals(1, storedVersion("versioned_user"));

        String second = login("versioned_user", "pw123456");
        mockMvc.perform(post("/auth/logout").header("Authorization", "Bearer " + second))
                .andExpect(status().isNoContent());
        assertEquals(2, storedVersion("versioned_user"));
    }

    /** Loading a vanished user used to escape the filter as a server error. */
    @Test
    void tokenForADeletedAccountReturnsUnauthorized() throws Exception {
        String token = register("deleted_user", "pw123456");
        userRepository.delete(userRepository.findByUsername("deleted_user").orElseThrow());

        mockMvc.perform(get("/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginResponseCarriesTheRole() throws Exception {
        register("role_login_user", "pw123456");
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginRequest("role_login_user", "pw123456"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("role_login_user"))
                .andExpect(jsonPath("$.role").value("CONTESTANT"));
    }

    @Test
    void wrongPasswordReturnsTheErrorBodyTheClientParses() throws Exception {
        register("error_body_user", "pw123456");
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginRequest("error_body_user", "nope"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid username or password"));
    }

    @Test
    void shortPasswordReturnsFieldErrorsTheClientParses() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RegisterRequest("short_pw_user", "short"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation failed"))
                .andExpect(jsonPath("$.fields.password").exists());
    }

    /** The client relies on the old `/api/auth/*` path being gone, not aliased. */
    @Test
    void authRoutesAreNotUnderApiPrefix() throws Exception {
        register("prefix_user", "pw123456");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginRequest("prefix_user", "pw123456"))))
                .andExpect(status().isUnauthorized());
    }

    private int storedVersion(String username) {
        return userRepository.findByUsername(username).orElseThrow().getTokenVersion();
    }

    private String login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginRequest(username, password))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asString();
    }

    private String register(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RegisterRequest(username, password))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asString();
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
