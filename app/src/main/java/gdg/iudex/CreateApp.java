package gdg.iudex;

import gdg.iudex.auth.Access;
import gdg.iudex.auth.AuthController;
import gdg.iudex.auth.AuthMiddleware;
import gdg.iudex.auth.AuthService;
import gdg.iudex.auth.JwtTokenService;
import gdg.iudex.auth.LoginRateLimiter;
import gdg.iudex.auth.PasswordHasher;
import gdg.iudex.auth.RevocationCache;
import gdg.iudex.auth.TokenService;
import gdg.iudex.config.ServerConfig;
import gdg.iudex.errors.ErrorHandlers;
import gdg.iudex.models.Role;
import gdg.iudex.repositories.RevokedTokenDao;
import gdg.iudex.repositories.UserDao;

import io.javalin.Javalin;
import io.javalin.config.JavalinConfig;
import org.jdbi.v3.core.Jdbi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class CreateApp {

    private static final Logger log = LoggerFactory.getLogger(CreateApp.class);

    /** How often expired revocations and rate-limit entries are swept. */
    private static final Duration CLEANUP_INTERVAL = Duration.ofHours(1);

    public static Javalin create(Jdbi jdbi) {
        return create(jdbi, ServerConfig.fromEnvironment(), null);
    }

    public static Javalin create(
            Jdbi jdbi,
            Consumer<JavalinConfig> extraConfig
    ) {
        return create(jdbi, ServerConfig.fromEnvironment(), extraConfig);
    }

    public static Javalin create(
            Jdbi jdbi,
            ServerConfig serverConfig,
            Consumer<JavalinConfig> extraConfig
    ) {

        // ---------------------------------------------------------
        // Repositories
        // ---------------------------------------------------------

        UserDao userDao = jdbi.onDemand(UserDao.class);
        RevokedTokenDao revokedTokenDao =
                jdbi.onDemand(RevokedTokenDao.class);

        // ---------------------------------------------------------
        // Services
        // ---------------------------------------------------------

        PasswordHasher passwordHasher = new PasswordHasher();

        RevocationCache revocationCache =
                new RevocationCache(revokedTokenDao);

        TokenService tokenService = new JwtTokenService(
                serverConfig.jwtSecret(),
                serverConfig.tokenLifetime(),
                revocationCache
        );

        AuthService authService = new AuthService(
                userDao,
                passwordHasher
        );

        // 5 wrong passwords for one account, or 20 from one address,
        // buys a 15 minute pause. Generous for a contestant fumbling
        // a password, useless for guessing at any real speed.
        LoginRateLimiter perUsername =
                new LoginRateLimiter(5, Duration.ofMinutes(15));

        LoginRateLimiter perAddress =
                new LoginRateLimiter(20, Duration.ofMinutes(15));

        // THIS IS A VERY CRUDE TEST RIGHT NOW, PLEASE SET UP PROPER TESTS
        try {
            // We must hash the password before saving it to the DB so the AuthService can verify it later
            String hashedTestPassword = passwordHasher.hash("correct-password");
            userDao.insertUser("alice", hashedTestPassword, Role.CONTESTANT);
            log.warn("Test user 'alice' injected into database.");
        } catch (Exception e) {
            // Ignore if the user already exists (in case you switch to a persistent DB later)
            log.warn("Test user already exists or could not be created.");
        }

        // ---------------------------------------------------------
        // Controllers / Middleware
        // ---------------------------------------------------------

        AuthController authController = new AuthController(
                authService,
                tokenService,
                perUsername,
                perAddress
        );

        AuthMiddleware authMiddleware = new AuthMiddleware(
                tokenService
        );

        // ---------------------------------------------------------
        // Background cleanup
        //
        // Expired revocations can never change an outcome again, and
        // elapsed rate-limit windows are dead weight, so both are
        // swept periodically. Without this they accumulate for the
        // lifetime of the server.
        // ---------------------------------------------------------

        ScheduledExecutorService cleanup =
                Executors.newSingleThreadScheduledExecutor(runnable -> {
                    Thread thread = new Thread(runnable, "iudex-cleanup");
                    thread.setDaemon(true);
                    return thread;
                });

        cleanup.scheduleAtFixedRate(
                () -> {
                    try {
                        revocationCache.purgeExpired();
                        perUsername.purgeExpired();
                        perAddress.purgeExpired();
                    } catch (Exception e) {
                        // Never let a failure kill the schedule.
                        log.error("Scheduled cleanup failed", e);
                    }
                },
                CLEANUP_INTERVAL.toMinutes(),
                CLEANUP_INTERVAL.toMinutes(),
                TimeUnit.MINUTES
        );

        // ---------------------------------------------------------
        // Create Javalin application
        // ---------------------------------------------------------

        return Javalin.create(config -> {

            // Allow Main.java to provide additional configuration.
            if (extraConfig != null) {
                extraConfig.accept(config);
            }

            config.events.serverStopped(cleanup::shutdownNow);

            // -----------------------------------------------------
            // CORS
            //
            // Only origins we were told about. anyHost() would let
            // any page on the internet script this API through a
            // visitor's browser.
            // -----------------------------------------------------

            if (serverConfig.allowedOrigins().isEmpty()) {
                log.warn(
                    "ALLOWED_ORIGINS is not set - cross-origin browser "
                    + "requests will be refused. Set it to your client's "
                    + "origin, e.g. http://localhost:5173");
            } else {
                config.bundledPlugins.enableCors(cors ->
                    cors.addRule(rule ->
                        serverConfig.allowedOrigins()
                            .forEach(rule::allowHost)));

                log.info("CORS enabled for {}", serverConfig.allowedOrigins());
            }

            // -----------------------------------------------------
            // Access control
            //
            // One handler for every matched route. Each route below
            // declares who may reach it; anything that declares
            // nothing is refused rather than left open.
            // -----------------------------------------------------

            config.routes.beforeMatched(authMiddleware::enforce);

            // -----------------------------------------------------
            // Routes
            // -----------------------------------------------------

            config.routes.get(
                    "/api/health",
                    ctx -> ctx.json(Map.of("status", "live")),
                    Access.PUBLIC
            );

            config.routes.post(
                    "/api/auth/login",
                    authController::login,
                    Access.PUBLIC
            );

            config.routes.post(
                    "/api/auth/logout",
                    authController::logout,
                    Access.authenticated()
            );

            // -----------------------------------------------------
            // Exception handlers
            //
            // IMPORTANT:
            // Javalin 7 requires these to be registered through
            // config.routes during application creation.
            // -----------------------------------------------------

            ErrorHandlers.register(config.routes);
        });
    }
}
