package gdg.iudex.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import org.flywaydb.core.Flyway;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.sqlobject.SqlObjectPlugin;

/**
 *  class Database
 *  
 *  A wrapper around your java database interface (jdbi).
 *  In our case, Hikari offers the connection pool (CP).
 *  Flyway exists because SQL migration.
 *
 *  @dataSource - Hikari DataSource
 *  @jdbi - jdbi (duh)
 *  
 *  Constructors:
 *  Database(String jdbiurl)
 *  
 *  Methods:
 *  jdbi() - returns @jdbi
 *  close() - closes @dataSource
 *  
 *  see usage in tests
 */

public final class Database implements AutoCloseable {

    private final HikariDataSource dataSource;
    private final Jdbi jdbi;

    public Database(String jdbcUrl) {

        HikariConfig config = new HikariConfig();

        config.setJdbcUrl(jdbcUrl);
        config.setUsername("sa");
        config.setPassword("");

        this.dataSource = new HikariDataSource(config);

        Flyway.configure()
        .dataSource(dataSource)
        .load()
        .migrate();

        this.jdbi = Jdbi.create(dataSource).installPlugin(new SqlObjectPlugin());
    }

    public Jdbi jdbi() {
        return jdbi;
    }

    @Override
    public void close() {
        dataSource.close();
    }
}