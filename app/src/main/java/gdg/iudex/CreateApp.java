package gdg.iudex;

import gdg.iudex.auth.AuthController;
import gdg.iudex.auth.AuthMiddleware;
import gdg.iudex.auth.AuthService;
import gdg.iudex.auth.AuthenticationException;
import gdg.iudex.auth.JwtTokenService;
import gdg.iudex.auth.PasswordHasher;
import gdg.iudex.repositories.RevokedTokenDao;
import gdg.iudex.repositories.UserDao;

import io.javalin.Javalin;
import io.javalin.config.JavalinConfig;
import org.jdbi.v3.core.Jdbi;

import java.time.Duration;
import java.util.Map;
import java.util.function.Consumer;

public class CreateApp {

    public static Javalin create(Jdbi jdbi) {
        return create(jdbi, null);
    }

    public static Javalin create(
            Jdbi jdbi,
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

        JwtTokenService tokenService = new JwtTokenService(
                Duration.ofHours(24),
                revokedTokenDao
        );

        AuthService authService = new AuthService(
                userDao,
                passwordHasher
        );

        // THIS IS A VERY CRUDE TEST RIGHT NOW, PLEASE SET UP PROPER TESTS
        try {
            // We must hash the password before saving it to the DB so the AuthService can verify it later
            String hashedTestPassword = passwordHasher.hash("correct-password");
            userDao.insertUser("alice", hashedTestPassword, "CONTESTANT");
            System.out.println("Test user 'alice' injected into database.");
        } catch (Exception e) {
            // Ignore if the user already exists (in case you switch to a persistent DB later)
            System.out.println("Test user already exists or could not be created.");
        }

        // ---------------------------------------------------------
        // Controllers / Middleware
        // ---------------------------------------------------------

        AuthController authController = new AuthController(
                authService,
                tokenService
        );

        AuthMiddleware authMiddleware = new AuthMiddleware(
                tokenService
        );

        // ---------------------------------------------------------
        // Create Javalin application
        // ---------------------------------------------------------

        return Javalin.create(config -> {

            // Allow Main.java to provide additional configuration.
            if (extraConfig != null) {
                extraConfig.accept(config);
            }

            // -----------------------------------------------------
            // CORS
            // -----------------------------------------------------

            config.bundledPlugins.enableCors(cors -> {
                cors.addRule(rule -> rule.anyHost());
            });

            // -----------------------------------------------------
            // Routes
            // -----------------------------------------------------

            config.routes.get("/api/health", ctx -> {
                ctx.json(Map.of(
                        "status", "live"
                ));
            });

            // -----------------------------------------------------
            // Public authentication routes
            // -----------------------------------------------------

            config.routes.post(
                    "/api/auth/login",
                    authController::login
            );

            // -----------------------------------------------------
            // Protected authentication routes
            // -----------------------------------------------------

            config.routes.post(
                    "/api/auth/logout",
                    ctx -> {
                        authMiddleware.requireAuthentication(ctx);
                        authController.logout(ctx);
                    }
            );

            // -----------------------------------------------------
            // Exception handlers
            //
            // IMPORTANT:
            // Javalin 7 requires these to be registered through
            // config.routes during application creation.
            // -----------------------------------------------------

            config.routes.exception(
                    AuthenticationException.class,
                    (exception, ctx) -> {
                        ctx.status(401);
                        ctx.json(Map.of(
                                "error",
                                exception.getMessage()
                        ));
                    }
            );
        });
    }
}