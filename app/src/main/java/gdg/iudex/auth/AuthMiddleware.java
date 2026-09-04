package gdg.iudex.auth;

import io.javalin.http.Context;
import io.javalin.http.UnauthorizedResponse;

// DISCLAIMER: THIS IS VIBECODED

public class AuthMiddleware {

    private final JwtTokenService tokenService;

    public AuthMiddleware(JwtTokenService tokenService) {
        this.tokenService = tokenService;
    }

    // Call this before any protected route
    public void requireAuthentication(Context ctx) {
        String token = AuthController.extractToken(ctx);
        
        if (token == null) {
            throw new UnauthorizedResponse("Missing or invalid Authorization header");
        }

        // Will throw AuthenticationException if token is invalid or revoked
        AuthenticatedUser user = tokenService.verify(token);
        
        // Attach user to context so the route handler knows who is logged in
        ctx.attribute("user", user); 
    }
}