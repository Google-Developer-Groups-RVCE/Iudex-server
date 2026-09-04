package gdg.iudex;

import gdg.iudex.config.ServerConfig;
import gdg.iudex.db.Database;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {

        ServerConfig serverConfig = ServerConfig.fromEnvironment();

        // Initialize database.
        // The url is configurable because the default is relative to
        // the working directory, which is not always where you think
        // it is - launched through Gradle it resolves inside app/.
        // Logged at startup so the location is never a mystery.
        // NOTE: MAKE SURE THIS IS IN GITIGNORE. DO NOT PUSH THIS EVER
        log.info("Opening database {}", serverConfig.databaseUrl());

        Database database = new Database(serverConfig.databaseUrl());

        // Create application.
        var app = CreateApp.create(database.jdbi(), serverConfig, config -> {

            // Javalin 7 lifecycle events are configured directly
            // through config.events.
            config.events.serverStopped(() -> {
                log.info("Shutting down... closing database.");

                database.close();
            });
        });

        // Start server.
        app.start(serverConfig.port());

        log.info("Iudex server started on http://localhost:{}",
                serverConfig.port());
    }
}
