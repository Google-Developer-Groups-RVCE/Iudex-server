package gdg.iudex.auth;

import gdg.iudex.db.Database;
import gdg.iudex.errors.ErrorHandlers;
import gdg.iudex.models.Role;
import gdg.iudex.models.User;
import gdg.iudex.repositories.UserDao;

import io.javalin.Javalin;
import io.javalin.testtools.JavalinTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 *  class AuthMiddlewareTest
 *
 *  Tests that access control is enforced centrally, using a small
 *  purpose built app rather than the real routes, so it can include
 *  the case that matters most: a route somebody registered without
 *  saying who may reach it.
 *
 *  Tests:
 *  publicRouteNeedsNoToken - PUBLIC really is public.
 *  undeclaredRouteIsRefused - forgetting to declare access fails closed.
 *  missingTokenIsRejected - 401, as JSON.
 *  badTokenIsRejected - 401, as JSON.
 *  matchingRoleIsAllowed - the role on the token opens the route.
 *  wrongRoleIsForbidden - 403, which is not the same as 401.
 *  verifiedUserIsOnTheContext - handlers need not re-parse the token.
 *  tokenIsVerifiedOnlyOnce - logout reuses what the middleware found.
 */

class AuthMiddlewareTest {

    private static final String GOOD_TOKEN = "good-token";

    /** Stands in for the real token service, and counts its calls. */
    private static final class StubTokenService implements TokenService {

        private final AuthenticatedUser user;

        int verifyCalls;
        int revokeCalls;
        AuthenticatedUser revoked;

        StubTokenService(Role role) {
            this.user = new AuthenticatedUser(
                7L, role, "token-id", Instant.now().plusSeconds(900));
        }

        @Override public String issue(User user) { return GOOD_TOKEN; }

        @Override public AuthenticatedUser verify(String token) {
            verifyCalls++;

            if (!GOOD_TOKEN.equals(token)) {
                throw new AuthenticationException("Invalid or expired token");
            }

            return user;
        }

        @Override public void revoke(AuthenticatedUser user) {
            revokeCalls++;
            revoked = user;
        }
    }

    private Database database;

    @BeforeEach
    void setUp() {
        database = new Database("jdbc:h2:mem:" + UUID.randomUUID());
    }

    @AfterEach
    void tearDown() {
        database.close();
    }

    /** An app whose routes cover every access case worth checking. */
    private Javalin appFor(StubTokenService tokenService) {

        AuthMiddleware middleware = new AuthMiddleware(tokenService);

        UserDao userDao = database.jdbi().onDemand(UserDao.class);

        // The hasher is stubbed out because none of these tests log
        // in, and a real Argon2 hash per test is wasted time.
        AuthService authService = new AuthService(userDao, new PasswordHasher() {
            @Override public String hash(String password) { return "unused"; }
            @Override public boolean verify(String p, String h) { return false; }
        });

        AuthController controller = new AuthController(
            authService,
            tokenService,
            new LoginRateLimiter(5, Duration.ofMinutes(15)),
            new LoginRateLimiter(20, Duration.ofMinutes(15))
        );

        return Javalin.create(config -> {
            config.routes.beforeMatched(middleware::enforce);

            config.routes.get("/open", ctx -> ctx.result("open"),
                Access.PUBLIC);

            // Deliberately registered with no access declared, the way
            // a route added in a hurry would be.
            config.routes.get("/undeclared", ctx -> ctx.result("leaked"));

            config.routes.get("/contestant", ctx -> ctx.result("contestant"),
                Access.CONTESTANT);

            config.routes.get("/admin", ctx -> ctx.result("admin"),
                Access.ADMINISTRATOR);

            config.routes.get("/whoami",
                ctx -> ctx.result(
                    Long.toString(AuthMiddleware.currentUser(ctx).userId())),
                Access.authenticated());

            config.routes.post("/logout", controller::logout,
                Access.authenticated());

            ErrorHandlers.register(config.routes);
        });
    }

    private static void withToken(io.javalin.testtools.Request.Builder req) {
        req.header("Authorization", "Bearer " + GOOD_TOKEN);
    }

    @Test
    void publicRouteNeedsNoToken() {
        JavalinTest.test(appFor(new StubTokenService(Role.CONTESTANT)),
            (server, client) -> {
                var response = client.get("/open");

                assertEquals(200, response.code());
                assertEquals("open", response.body().string());
            });
    }

    @Test
    void undeclaredRouteIsRefused() {
        JavalinTest.test(appFor(new StubTokenService(Role.ADMINISTRATOR)),
            (server, client) -> {
                var response = client.get("/undeclared",
                    AuthMiddlewareTest::withToken);

                assertEquals(403, response.code(),
                    "A route that declares no access must fail closed");

                assertNotEquals("leaked", response.body().string(),
                    "The handler must not have run");
            });
    }

    @Test
    void missingTokenIsRejected() {
        JavalinTest.test(appFor(new StubTokenService(Role.CONTESTANT)),
            (server, client) -> {
                var response = client.get("/contestant");

                assertEquals(401, response.code());
                assertTrue(response.body().string().contains("\"error\""));
            });
    }

    @Test
    void badTokenIsRejected() {
        JavalinTest.test(appFor(new StubTokenService(Role.CONTESTANT)),
            (server, client) -> {
                var response = client.get("/contestant",
                    req -> req.header("Authorization", "Bearer nonsense"));

                assertEquals(401, response.code());
                assertTrue(response.body().string().contains("\"error\""));
            });
    }

    @Test
    void matchingRoleIsAllowed() {
        JavalinTest.test(appFor(new StubTokenService(Role.CONTESTANT)),
            (server, client) -> {
                var response = client.get("/contestant",
                    AuthMiddlewareTest::withToken);

                assertEquals(200, response.code());
                assertEquals("contestant", response.body().string());
            });
    }

    @Test
    void wrongRoleIsForbidden() {
        JavalinTest.test(appFor(new StubTokenService(Role.CONTESTANT)),
            (server, client) -> {
                var response = client.get("/admin",
                    AuthMiddlewareTest::withToken);

                assertEquals(403, response.code(),
                    "A known user lacking the role is forbidden, not "
                    + "unauthenticated - logging in again would not help");

                assertTrue(response.body().string().contains("\"error\""));
            });
    }

    @Test
    void verifiedUserIsOnTheContext() {
        JavalinTest.test(appFor(new StubTokenService(Role.CONTESTMASTER)),
            (server, client) -> {
                var response = client.get("/whoami",
                    AuthMiddlewareTest::withToken);

                assertEquals(200, response.code());
                assertEquals("7", response.body().string());
            });
    }

    @Test
    void tokenIsVerifiedOnlyOnce() {
        StubTokenService tokenService = new StubTokenService(Role.CONTESTANT);

        JavalinTest.test(appFor(tokenService), (server, client) -> {
            var response = client.post("/logout", "",
                AuthMiddlewareTest::withToken);

            assertEquals(200, response.code());

            assertEquals(1, tokenService.verifyCalls,
                "Logout should reuse the middleware's result, not "
                + "verify the same token a second time");

            assertEquals(1, tokenService.revokeCalls);

            assertEquals("token-id", tokenService.revoked.tokenId(),
                "The revoked token must be the one that was verified");
        });
    }
}
