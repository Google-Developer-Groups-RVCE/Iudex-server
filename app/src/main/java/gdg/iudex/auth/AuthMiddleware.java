package gdg.iudex.auth;

import io.javalin.http.Context;
import io.javalin.http.HandlerType;
import io.javalin.security.RouteRole;

import java.util.Set;

/**
 *  class AuthMiddleware
 *
 *  The single place authentication and authorisation are enforced.
 *
 *  Registered once as a beforeMatched handler, so it runs for every
 *  matched route without the route's own handler having to remember
 *  to call it. A route states who may reach it by declaring Access
 *  values at registration; a route that declares nothing is refused,
 *  so an endpoint added without a moment's thought about access fails
 *  closed rather than being quietly open to the world.
 */

public class AuthMiddleware {

    /** Context attribute holding the verified caller. */
    public static final String USER_ATTRIBUTE = "user";

    private final TokenService tokenService;

    public AuthMiddleware(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    public void enforce(Context ctx) {

        // CORS preflight carries no credentials by design, and the
        // CORS plugin answers it. Nothing sensitive is returned.
        if (ctx.method() == HandlerType.OPTIONS) {
            return;
        }

        Set<RouteRole> permitted = ctx.routeRoles();

        if (permitted.isEmpty()) {
            throw new AuthorizationException(
                "Route declares no access level"
            );
        }

        if (permitted.contains(Access.PUBLIC)) {
            return;
        }

        AuthenticatedUser user = authenticate(ctx);

        if (!permitted.contains(Access.of(user.role()))) {
            throw new AuthorizationException();
        }
    }

    /**
     *  Verifies the bearer token and attaches the caller to the
     *  context, so handlers can read who is logged in without
     *  parsing the token a second time.
     */
    private AuthenticatedUser authenticate(Context ctx) {
        String token = extractToken(ctx);

        if (token == null) {
            throw new AuthenticationException(
                "Missing or invalid Authorization header"
            );
        }

        // Throws AuthenticationException if invalid, expired or revoked.
        AuthenticatedUser user = tokenService.verify(token);

        ctx.attribute(USER_ATTRIBUTE, user);

        return user;
    }

    /** Pulls the token out of "Authorization: Bearer <token>". */
    private static String extractToken(Context ctx) {
        String header = ctx.header("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }

        return null;
    }

    /** The caller this request was verified as. */
    public static AuthenticatedUser currentUser(Context ctx) {
        AuthenticatedUser user = ctx.attribute(USER_ATTRIBUTE);

        if (user == null) {
            // Only reachable if a protected handler is somehow run
            // without the middleware, which would be a wiring bug.
            throw new IllegalStateException(
                "No authenticated user on a protected route"
            );
        }

        return user;
    }
}
