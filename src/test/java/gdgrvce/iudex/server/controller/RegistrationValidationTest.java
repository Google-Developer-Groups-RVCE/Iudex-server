package gdgrvce.iudex.server.controller;

import gdgrvce.iudex.server.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers the request constraints on the auth endpoints.
 *
 * <p>These used to be absent: a null username reached the database and came back
 * as a server error, and a blank one was accepted outright, creating an account
 * that could never sign in again because an empty JWT subject is dropped from
 * the token.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
class RegistrationValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    // ---------- username ----------

    @Test
    void rejectsANullUsername() throws Exception {
        register("{\"username\":null,\"password\":\"pw123456\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation failed"))
                .andExpect(jsonPath("$.fields.username").isNotEmpty());
    }

    @Test
    void rejectsABlankUsername() throws Exception {
        register("{\"username\":\"\",\"password\":\"pw123456\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.username").isNotEmpty());
    }

    @Test
    void rejectsAWhitespaceOnlyUsername() throws Exception {
        register("{\"username\":\"   \",\"password\":\"pw123456\"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsAMissingUsernameField() throws Exception {
        register("{\"password\":\"pw123456\"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsAUsernameThatIsTooShort() throws Exception {
        register("{\"username\":\"ab\",\"password\":\"pw123456\"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsAUsernameThatIsTooLong() throws Exception {
        register("{\"username\":\"" + "a".repeat(51) + "\",\"password\":\"pw123456\"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsAUsernameWithSpacesOrControlCharacters() throws Exception {
        register("{\"username\":\"has space\",\"password\":\"pw123456\"}")
                .andExpect(status().isBadRequest());
        register("{\"username\":\"has\\ttab\",\"password\":\"pw123456\"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void acceptsTheUsernameCharactersTheRuleAllows() throws Exception {
        register("{\"username\":\"ok.user_name-1\",\"password\":\"pw123456\"}")
                .andExpect(status().isCreated());
    }

    // ---------- password ----------

    @Test
    void rejectsANullPassword() throws Exception {
        register("{\"username\":\"nullpw_user\",\"password\":null}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.password").isNotEmpty());
    }

    @Test
    void rejectsABlankPassword() throws Exception {
        register("{\"username\":\"blankpw_user\",\"password\":\"\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.password").isNotEmpty());
    }

    @Test
    void rejectsAPasswordShorterThanTheMinimum() throws Exception {
        register("{\"username\":\"shortpw_user\",\"password\":\"pw12345\"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsAPasswordLongerThanBcryptReads() throws Exception {
        // Beyond 72 bytes BCrypt ignores the rest, so accepting it would be a lie.
        register("{\"username\":\"longpw_user\",\"password\":\"" + "a".repeat(73) + "\"}")
                .andExpect(status().isBadRequest());
    }

    // ---------- nothing is created when validation fails ----------

    @Test
    void aRejectedRegistrationCreatesNoAccount() throws Exception {
        register("{\"username\":\"\",\"password\":\"pw123456\"}")
                .andExpect(status().isBadRequest());
        register("{\"username\":\"rejected_user\",\"password\":\"short\"}")
                .andExpect(status().isBadRequest());

        assertTrue(userRepository.findByUsername("").isEmpty(), "the blank username must not exist");
        assertTrue(userRepository.findByUsername("rejected_user").isEmpty(),
                "a rejected registration must leave no account behind");
    }

    @Test
    void reportsEveryOffendingFieldAtOnce() throws Exception {
        register("{\"username\":\"\",\"password\":\"\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.username").isNotEmpty())
                .andExpect(jsonPath("$.fields.password").isNotEmpty());
    }

    // ---------- login stays lenient ----------

    @Test
    void loginRejectsABlankUsernameOrPassword() throws Exception {
        login("{\"username\":\"\",\"password\":\"pw123456\"}").andExpect(status().isBadRequest());
        login("{\"username\":\"someone\",\"password\":\"\"}").andExpect(status().isBadRequest());
    }

    @Test
    void loginDoesNotApplyTheRegistrationRules() throws Exception {
        // A short password is a failed login, not a validation error: replying
        // "too short" would describe the policy to whoever is guessing.
        login("{\"username\":\"nobody_here\",\"password\":\"x\"}")
                .andExpect(status().isUnauthorized());
    }

    private org.springframework.test.web.servlet.ResultActions register(String body) throws Exception {
        return mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private org.springframework.test.web.servlet.ResultActions login(String body) throws Exception {
        return mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
