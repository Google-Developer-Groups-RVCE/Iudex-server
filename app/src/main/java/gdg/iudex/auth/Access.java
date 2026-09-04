package gdg.iudex.auth;

import gdg.iudex.models.Role;
import io.javalin.security.RouteRole;

import java.util.Arrays;

/**
 *  enum Access
 *
 *  Who is allowed to call a route.
 *
 *  Every route declares its own access level at registration time, and
 *  AuthMiddleware enforces it for all of them from one place. A route
 *  that declares nothing is denied, so forgetting to think about access
 *  fails loudly instead of silently publishing an open endpoint.
 *
 *  This mirrors Role rather than reusing it directly, so the domain
 *  model does not have to implement a Javalin interface.
 */

public enum Access implements RouteRole {

    /** No token required. */
    PUBLIC,

    CONTESTANT,
    CONTESTMASTER,
    ADMINISTRATOR;

    static {
        // Fails at startup, not mid-request, if a Role is ever added
        // without the matching Access.
        for (Role role : Role.values()) {
            try {
                valueOf(role.name());
            } catch (IllegalArgumentException e) {
                throw new ExceptionInInitializerError(
                    "Role." + role.name() + " has no matching Access value");
            }
        }
    }

    /** The Access matching a signed-in user's role. */
    public static Access of(Role role) {
        return valueOf(role.name());
    }

    /** Any signed-in user, whatever their role. */
    public static Access[] authenticated() {
        return Arrays.stream(values())
                .filter(access -> access != PUBLIC)
                .toArray(Access[]::new);
    }
}
