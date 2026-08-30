package gdg.iudex;

import io.javalin.Javalin;
import org.jdbi.v3.core.Jdbi;

public class CreateApp {

    public static Javalin create(Jdbi jdbi) {
        return Javalin.create(config -> {

            config.bundledPlugins.enableCors(cors -> {
                cors.addRule(it -> it.anyHost());
            });

            // Routes
            config.routes.get("/api/health", ctx -> {
                ctx.json("{ \"status\": \"live\" }");
            });

            // Example:
            // config.routes.post("/api/auth/login",
            //         ctx -> new AuthController(jdbi).login(ctx));
        });
    }
}