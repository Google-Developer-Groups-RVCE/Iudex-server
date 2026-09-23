package gdgrvce.iudex.server.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Checks what the API exposes without a token, and what the shipped defaults are. */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void theDatabaseConsoleIsNotReachableWithoutATokenWhenDisabled() throws Exception {
        // With the console off there is no permitAll rule for it, so the path
        // falls through to the catch-all and is rejected like anything else.
        mockMvc.perform(get("/h2-console")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/h2-console/")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/h2-console/login.do")).andExpect(status().isUnauthorized());
    }

    @Test
    void onlyTheAuthEntryPointsArePublic() throws Exception {
        mockMvc.perform(post("/auth/register")).andExpect(status().isBadRequest());
        mockMvc.perform(post("/auth/login")).andExpect(status().isBadRequest());

        mockMvc.perform(get("/auth/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/contests")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/users")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/submissions")).andExpect(status().isUnauthorized());
    }

    @Test
    void theShippedDefaultsKeepTheConsoleOffAndHoldNoDatabasePassword() throws Exception {
        // Read from source, not the classpath: test resources shadow the real
        // file there, so a classpath read would check the wrong properties.
        String defaults = Files.readString(Path.of("src/main/resources/application.properties"));

        assertTrue(defaults.contains("spring.h2.console.enabled=false"),
                "the console must ship disabled");
        assertFalse(defaults.contains("spring.datasource.password=password"),
                "no database password belongs in version control");
        assertFalse(defaults.contains("jdbc:h2:file:"),
                "the default database must not write a file holding those credentials");
    }
}
