package gdg.iudex.auth;

import gdg.iudex.errors.ApiException;
import gdg.iudex.models.LoginRequest;
import gdg.iudex.models.TokenResponse;
import gdg.iudex.models.User;

import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.Map;

public class AuthController {

    private static final Logger log =
            LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;
    private final TokenService tokenService;

    /*
     * Two budgets, because they defend against different things.
     * The per-username limit stops one account being ground through a
     * password list from anywhere; the per-address limit, which is
     * looser so a shared office connection is not locked out by one
     * person's typos, caps how much Argon2 work a single source can
     * make this server do.
     */
    private final LoginRateLimiter perUsername;
    private final LoginRateLimiter perAddress;

    public AuthController(
            AuthService authService,
            TokenService tokenService,
            LoginRateLimiter perUsername,
            LoginRateLimiter perAddress) {

        this.authService = authService;
        this.tokenService = tokenService;
        this.perUsername = perUsername;
        this.perAddress = perAddress;
    }

    public void login(Context ctx) {

        LoginRequest req = readLoginRequest(ctx);

        String usernameKey = req.username().toLowerCase(Locale.ROOT);
        String addressKey = ctx.ip();

        // Checked before authenticating, so a caller who is already
        // over budget never reaches the expensive hash.
        perUsername.checkAllowed(usernameKey);
        perAddress.checkAllowed(addressKey);

        User user;

        try {
            user = authService.authenticate(req.username(), req.password());

        } catch (AuthenticationException e) {
            perUsername.recordFailure(usernameKey);
            perAddress.recordFailure(addressKey);

            log.info("Failed login for '{}' from {}",
                    req.username(), addressKey);

            throw e;
        }

        perUsername.clear(usernameKey);
        perAddress.clear(addressKey);

        log.info("User {} logged in", user.id());

        ctx.json(new TokenResponse(tokenService.issue(user)));
    }

    public void logout(Context ctx) {

        // The middleware already verified this token and left the
        // result on the context, so there is nothing to re-parse.
        AuthenticatedUser user = AuthMiddleware.currentUser(ctx);

        tokenService.revoke(user);

        log.info("User {} logged out", user.userId());

        ctx.json(Map.of("message", "Successfully logged out"));
    }

    /**
     *  Reads and checks the request body.
     *
     *  A body that is not JSON, or is JSON but missing fields, is the
     *  caller's mistake and must be reported as a 400. Letting the
     *  parse failure escape would surface as a 500, which tells every
     *  client that the server is broken and worth retrying.
     */
    private static LoginRequest readLoginRequest(Context ctx) {

        LoginRequest req;

        try {
            req = ctx.bodyAsClass(LoginRequest.class);

        } catch (Exception e) {
            throw new ApiException(400, "Request body must be valid JSON", e);
        }

        if (req == null
                || req.username() == null || req.username().isBlank()
                || req.password() == null || req.password().isBlank()) {

            throw new ApiException(
                400, "Both 'username' and 'password' are required");
        }

        return req;
    }
}
