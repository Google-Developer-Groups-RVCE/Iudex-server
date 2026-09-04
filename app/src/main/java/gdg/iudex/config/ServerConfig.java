package gdg.iudex.config;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

/**
 *  record ServerConfig
 *
 *  Everything the server reads from its environment, resolved once at
 *  startup so the rest of the code never calls System.getenv and tests
 *  can build a config by hand.
 *
 *  Environment variables:
 *  PORT            - port to listen on (default 8080)
 *  JWT_SECRET      - token signing secret, required, min 32 bytes
 *  IUDEX_DB_URL    - JDBC url (default a file database next to the app)
 *  ALLOWED_ORIGINS - comma separated origins allowed to call this API
 *                    from a browser. Unset means none.
 */

public record ServerConfig(
    int port,
    String jwtSecret,
    Duration tokenLifetime,
    String databaseUrl,
    List<String> allowedOrigins
) {

    public static final String DEFAULT_DATABASE_URL = "jdbc:h2:file:./iudex_db";

    public static ServerConfig fromEnvironment() {

        String envPort = System.getenv("PORT");

        int port = (envPort != null && !envPort.isBlank())
                ? Integer.parseInt(envPort)
                : 8080;

        String databaseUrl = envOrDefault("IUDEX_DB_URL", DEFAULT_DATABASE_URL);

        return new ServerConfig(
            port,
            System.getenv("JWT_SECRET"),
            Duration.ofHours(24),
            databaseUrl,
            parseOrigins(System.getenv("ALLOWED_ORIGINS"))
        );
    }

    /**
     *  Splits a comma separated origin list, ignoring surrounding
     *  whitespace and empty entries. Null or blank means no origin is
     *  allowed, which is the safe default.
     */
    public static List<String> parseOrigins(String origins) {

        if (origins == null || origins.isBlank()) {
            return List.of();
        }

        return Arrays.stream(origins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .toList();
    }

    private static String envOrDefault(String name, String fallback) {
        String value = System.getenv(name);
        return (value == null || value.isBlank()) ? fallback : value;
    }
}
