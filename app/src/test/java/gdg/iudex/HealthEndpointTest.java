package gdg.iudex;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import gdg.iudex.config.ServerConfig;
import gdg.iudex.db.Database;

import io.javalin.testtools.JavalinTest;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 *  class HealthEndpointTest
 *
 *  Checks the health endpoint answers, and answers without a token.
 *
 *  The configuration is handed in rather than read from the
 *  environment, so a clean checkout can run the tests without anyone
 *  having exported JWT_SECRET first.
 */

public class HealthEndpointTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static ServerConfig testConfig() {
        return new ServerConfig(
            0,
            "a-test-secret-that-is-long-enough-to-be-accepted",
            Duration.ofMinutes(15),
            "unused",
            List.of()
        );
    }

    @Test
    public void healthEndpointReportsLive() {

        // Closed even if the assertions below throw, and given a
        // private name so it cannot collide with another test.
        try (Database database =
                 new Database("jdbc:h2:mem:" + UUID.randomUUID())) {

            var app = CreateApp.create(database.jdbi(), testConfig(), null);

            JavalinTest.test(app, (server, client) -> {
                var response = client.get("/api/health");

                assertEquals(200, response.code());

                // Compared as parsed JSON. Asserting on the exact
                // string made this fail over the spacing Jackson
                // happens to emit, which is not the endpoint's
                // promise to anyone.
                JsonNode body = JSON.readTree(response.body().string());

                assertTrue(body.has("status"), "No status field: " + body);
                assertEquals("live", body.get("status").asText());
            });
        }
    }
}
