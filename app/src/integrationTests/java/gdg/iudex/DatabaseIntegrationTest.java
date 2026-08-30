public class DatabaseIntegrationTest {
    @Test
    void databaseMigrationsWork() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1");
        config.setUsername("sa");
        config.setPassword("");

        try (HikariDataSource dataSource = new HikariDataSource(config)) {

            Flyway.configure()
                    .dataSource(dataSource)
                    .load()
                    .migrate();

            Jdbi jdbi = Jdbi.create(dataSource);

            // write something actually worth testing here
            // i'm lazy
        }
    }
}
