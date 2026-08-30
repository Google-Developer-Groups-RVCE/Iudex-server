package gdg.iudex;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.sqlobject.SqlObjectPlugin;

public class Main {
    public static void main(String[] args) {

        // get the port
        String envPort = System.getenv("PORT");
        int port = (envPort != null) ? Integer.parseInt(envPort) : 8080;

        // 1. Connection pool (HikariCP + H2) (CP lmao)
        HikariConfig config = new HikariConfig();

        // Store H2 database in ./iudex_data - NOTE - DO NOT PUSH THIS. EVER. LIKE SERIOUSLY.
        // IT'S IN .gitignore BUT MAKE SURE.
        config.setJdbcUrl("jdbc:h2:file:./iudex_data;AUTO_SERVER=TRUE");
        config.setUsername("sa");
        config.setPassword("");

        HikariDataSource dataSource = new HikariDataSource(config);

        // 2. Database migrations (Flyway) THIS IS IMPORTANT 
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .load();

        flyway.migrate();

        // 3. SQL Object Mapping (JDBI)
        Jdbi jdbi = Jdbi.create(dataSource);
        jdbi.installPlugin(new SqlObjectPlugin());

        // 4. Web Server (Javalin 7) FINALLY SOMETHING I UNDERSTAND
        var app = CreateApp.create(jdbi);
        app.start(port);

        System.out.println("Iudex server started on http://localhost:" + port);
    }
}