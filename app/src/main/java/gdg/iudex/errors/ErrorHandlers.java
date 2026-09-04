package gdg.iudex.errors;

import io.javalin.config.RoutesConfig;
import io.javalin.http.Context;
import io.javalin.http.HttpResponseException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 *  class ErrorHandlers
 *
 *  Renders every failure in one shape: {"error": "..."} as JSON.
 *
 *  Without this, errors leave by three different doors and look
 *  different depending on which one they used - our own exceptions as
 *  JSON, Javalin's built-in ones as plain text, and anything unhandled
 *  as an HTML-ish 500. A client cannot parse three shapes, so the
 *  rarest path is the one that breaks in front of a user.
 */

public final class ErrorHandlers {

    private static final Logger log =
            LoggerFactory.getLogger(ErrorHandlers.class);

    private ErrorHandlers() {}

    public static void register(RoutesConfig routes) {

        // Everything the application raises deliberately.
        routes.exception(ApiException.class, (exception, ctx) -> {

            if (exception instanceof RateLimitException rateLimited) {
                ctx.header(
                    "Retry-After",
                    Long.toString(Math.max(1, rateLimited.retryAfter().toSeconds()))
                );
            }

            respond(ctx, exception.status(), exception.getMessage());
        });

        // Javalin's own (NotFoundResponse, and anything a future
        // handler throws), which would otherwise render as plain text.
        routes.exception(HttpResponseException.class, (exception, ctx) ->
            respond(ctx, exception.getStatus(), exception.getMessage())
        );

        // The catch-all. The real cause is logged, never returned:
        // stack traces and driver messages are not the client's
        // business and often say more than we want them to.
        routes.exception(Exception.class, (exception, ctx) -> {
            log.error("Unhandled exception on {} {}",
                    ctx.method(), ctx.path(), exception);

            respond(ctx, 500, "Internal server error");
        });

        // Catches requests that matched no route at all. Guarded so
        // it cannot overwrite a 404 body an exception handler already
        // wrote above.
        routes.error(404, ctx -> {
            if (ctx.result() == null || ctx.result().isEmpty()) {
                respond(ctx, 404, "Not found");
            }
        });
    }

    private static void respond(Context ctx, int status, String message) {
        ctx.status(status).json(Map.of("error", message));
    }
}
