package gdg.iudex;

import gdg.iudex.config.ServerConfig;
import gdg.iudex.db.Database;

import io.javalin.testtools.JavalinTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 *  class ApiContractTest
 *
 *  End to end tests for the promises the API makes to a client:
 *  that protected routes are protected without the handler having to
 *  ask, that a bad request is reported as a bad request, and that
 *  every failure comes back in the same JSON shape.
 *
 *  Tests:
 *  healthIsPublic - the health route needs no token.
 *  protectedRouteRejectsMissingToken - no token means 401.
 *  protectedRouteRejectsGarbageToken - a junk token means 401.
 *  loginRejectsMalformedJson - a broken body is a 400, not a 500.
 *  loginRejectsMissingFields - valid JSON still needs both fields.
 *  loginThenLogout - the happy path works end to end.
 *  revokedTokenIsRejected - a token stops working after logout.
 *  unknownRouteReturnsJson - even 404 uses the shared error shape.
 *  repeatedFailuresAreRateLimited - guessing is cut off with a 429.
 *  allowedOriginGetsCorsHeader - the configured client may call us.
 *  otherOriginsGetNoCorsHeader - and nobody else may.
 */

class ApiContractTest {

    private Database database;
    private io.javalin.Javalin app;

    private static ServerConfig testConfig(String jdbcUrl) {
        return new ServerConfig(
            0,
            "a-test-secret-that-is-long-enough-to-be-accepted",
            Duration.ofMinutes(15),
            jdbcUrl,
            List.of()
        );
    }

    @BeforeEach
    void setUp() {
        // A private database per test, so tests cannot collide.
        database = new Database("jdbc:h2:mem:" + UUID.randomUUID());

        app = CreateApp.create(
            database.jdbi(),
            testConfig("unused"),
            null
        );
    }

    @AfterEach
    void tearDown() {
        database.close();
    }

    private static String errorOf(String body) {
        // The shared error shape is {"error":"..."}.
        assertTrue(body.contains("\"error\""),
            "Every error must use the shared JSON shape, got: " + body);
        return body;
    }

    @Test
    void healthIsPublic() {
        JavalinTest.test(app, (server, client) -> {
            var response = client.get("/api/health");

            assertEquals(200, response.code());
            assertTrue(response.body().string().contains("live"));
        });
    }

    @Test
    void protectedRouteRejectsMissingToken() {
        JavalinTest.test(app, (server, client) -> {
            var response = client.post("/api/auth/logout");

            assertEquals(401, response.code());
            assertTrue(
                response.headers().get("Content-Type").get(0).contains("json"),
                "Errors must be JSON, not the framework's plain text");
            errorOf(response.body().string());
        });
    }

    @Test
    void protectedRouteRejectsGarbageToken() {
        JavalinTest.test(app, (server, client) -> {
            var response = client.post("/api/auth/logout", "",
                req -> req.header("Authorization", "Bearer not-a-jwt"));

            assertEquals(401, response.code());
            errorOf(response.body().string());
        });
    }

    @Test
    void loginRejectsMalformedJson() {
        JavalinTest.test(app, (server, client) -> {
            var response = client.post("/api/auth/login", "not json at all");

            assertEquals(400, response.code(),
                "A body the client got wrong is a 400, never a 500");
            errorOf(response.body().string());
        });
    }

    @Test
    void loginRejectsMissingFields() {
        JavalinTest.test(app, (server, client) -> {
            var response = client.post("/api/auth/login", "{}");

            assertEquals(400, response.code());
            errorOf(response.body().string());
        });
    }

    @Test
    void loginThenLogout() {
        JavalinTest.test(app, (server, client) -> {
            String token = login(client);

            var response = client.post("/api/auth/logout", "",
                req -> req.header("Authorization", "Bearer " + token));

            assertEquals(200, response.code());
            assertTrue(response.body().string().contains("Successfully logged out"));
        });
    }

    @Test
    void revokedTokenIsRejected() {
        JavalinTest.test(app, (server, client) -> {
            String token = login(client);

            client.post("/api/auth/logout", "",
                req -> req.header("Authorization", "Bearer " + token));

            var second = client.post("/api/auth/logout", "",
                req -> req.header("Authorization", "Bearer " + token));

            assertEquals(401, second.code(),
                "A token must stop working once it has been revoked");
            assertTrue(second.body().string().contains("revoked"));
        });
    }

    @Test
    void unknownRouteReturnsJson() {
        JavalinTest.test(app, (server, client) -> {
            var response = client.get("/api/does-not-exist");

            assertEquals(404, response.code());
            errorOf(response.body().string());
        });
    }

    /** Logs in as the seeded user and returns the token. */
    private static String login(io.javalin.testtools.HttpClient client) throws Exception {
        var response = client.post("/api/auth/login",
            "{\"username\":\"alice\",\"password\":\"correct-password\"}");

        assertEquals(200, response.code());

        String body = response.body().string();

        return body.replaceAll(".*\"token\"\\s*:\\s*\"([^\"]+)\".*", "$1");
    }

    @Test
    void repeatedFailuresAreRateLimited() {
        JavalinTest.test(app, (server, client) -> {

            String wrong =
                "{\"username\":\"alice\",\"password\":\"wrong\"}";

            // The per-username budget is five failures.
            for (int attempt = 1; attempt <= 5; attempt++) {
                assertEquals(401,
                    client.post("/api/auth/login", wrong).code(),
                    "Attempt " + attempt + " is still within budget");
            }

            var blocked = client.post("/api/auth/login", wrong);

            assertEquals(429, blocked.code(),
                "The sixth attempt should be refused outright");

            assertNotNull(blocked.headers().get("Retry-After"),
                "A 429 should say when to come back");

            errorOf(blocked.body().string());

            // The block is on the account, not just on wrong guesses:
            // the right password is refused too while it holds.
            var correct = client.post("/api/auth/login",
                "{\"username\":\"alice\",\"password\":\"correct-password\"}");

            assertEquals(429, correct.code());
        });
    }

    /** An app that trusts exactly one browser origin. */
    private io.javalin.Javalin appAllowing(String origin) {
        return CreateApp.create(
            database.jdbi(),
            new ServerConfig(
                0,
                "a-test-secret-that-is-long-enough-to-be-accepted",
                Duration.ofMinutes(15),
                "unused",
                List.of(origin)
            ),
            null
        );
    }

    @Test
    void allowedOriginGetsCorsHeader() {
        JavalinTest.test(appAllowing("http://localhost:5173"),
            (server, client) -> {
                var response = client.get("/api/health",
                    req -> req.header("Origin", "http://localhost:5173"));

                assertEquals(
                    List.of("http://localhost:5173"),
                    response.headers().get("Access-Control-Allow-Origin"));
            });
    }

    @Test
    void otherOriginsGetNoCorsHeader() {
        JavalinTest.test(appAllowing("http://localhost:5173"),
            (server, client) -> {
                var response = client.get("/api/health",
                    req -> req.header("Origin", "https://evil.example"));

                // Absent headers come back as null, not an empty list.
                assertNull(
                    response.headers().get("Access-Control-Allow-Origin"),
                    "An origin we never allowed must not be told it is allowed");
            });
    }
}
