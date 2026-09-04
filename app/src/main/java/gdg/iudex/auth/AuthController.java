package gdg.iudex.auth;

import gdg.iudex.models.User;
import gdg.iudex.models.LoginRequest;
import gdg.iudex.models.TokenResponse;
import io.javalin.http.Context;

// DISCLAIMER: THIS IS VIBECODED

public class AuthController {

    private final AuthService authService;
    private final JwtTokenService tokenService;

    public AuthController(AuthService authService, JwtTokenService tokenService) {
        this.authService = authService;
        this.tokenService = tokenService;
    }

    public void login(Context ctx) {
        LoginRequest req = ctx.bodyAsClass(LoginRequest.class);

        User user = authService.authenticate(req.username(), req.password());

        String token = tokenService.issue(user);

        ctx.json(new TokenResponse(token));
    }

    public void logout(Context ctx) {
        String token = extractToken(ctx);
        if (token != null) {
            tokenService.revoke(token);
        }
        
        ctx.status(200).json("{ \"message\": \"Successfully logged out\" }");
    }

    // Helper to pull the token out of the "Authorization: Bearer <token>" header
    public static String extractToken(Context ctx) {
        String header = ctx.header("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7); // Strip "Bearer "
        }
        return null;
    }
}