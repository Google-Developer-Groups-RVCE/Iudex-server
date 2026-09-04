package gdg.iudex;

import gdg.iudex.db.Database;

public class Main {

    public static void main(String[] args) {

        // Resolve port from environment, defaulting to 8080.
        String envPort = System.getenv("PORT");

        int port = (envPort != null && !envPort.isBlank())
                ? Integer.parseInt(envPort)
                : 8080;

        // Initialize database.
        // NOTE: MAKE SURE THIS IS IN GITIGNORE. DO NOT PUSH THIS EVER
        Database database = new Database(
                "jdbc:h2:file:./iudex_db"
        );

        // Create application.
        var app = CreateApp.create(database.jdbi(), config -> {

            // Javalin 7 lifecycle events are configured directly
            // through config.events.
            config.events.serverStopped(() -> {
                System.out.println(
                        "Shutting down... closing database."
                );

                database.close();
            });
        });

        // Start server.
        app.start(port);

        System.out.println(
                "Iudex server started on http://localhost:" + port
        );
    }
}