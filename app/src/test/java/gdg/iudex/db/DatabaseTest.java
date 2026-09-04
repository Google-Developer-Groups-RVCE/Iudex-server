package gdg.iudex.db;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 *  class DatabaseTest
 *  
 *  Unit tests for class Database.
 *
 *  Tests:
 *  databaseStarts - Tests if the database starts.
 *  databaseClosesCorrectly - Tests if Database.close() works.
 *  invalidUrlFails - Tests if an invalid jdbc url fails.
 *  flywayMigrationsWork - Tests if Flyway is creating the users table.
 */

class DatabaseTest {

    @Test
    void databaseStarts() {

        try (Database database =
                new Database("jdbc:h2:mem:test_db;DB_CLOSE_DELAY=-1")) {
            // this specific jdbc url has the following meaning:
            // h2 is the database vendor
            // mem tells it to store it in RAM, meaning it disappears after this function ends
            // test_db is the name of the database file
            // setting DB_CLOSE_DELAY to -1 means that the database will persist
            // even when no connections are active

            // if jdbi is null, then initialization failed
            assertNotNull(database.jdbi());

            int result = database.jdbi().withHandle(handle ->
                handle.createQuery("SELECT 1")   // should simply return 1
                    .mapTo(int.class)           // should be an int
                    .one()                            // should be a single row
            );

            assertEquals(1, result);
        }
    }

    @Test
    void databaseClosesCorrectly() {
        Database database = new Database("jdbc:h2:mem:test_close;DB_CLOSE_DELAY=-1");
        
        // close the database
        database.close();

        // this should throw an exception
        // obv because database was closed
        assertThrows(Exception.class, () -> {
            database.jdbi().withHandle(handle ->
                handle.createQuery("SELECT 1")
                    .mapTo(int.class)
                    .one()
            );
        }, "Querying a closed database should throw an exception");
    }

    @Test
    void invalidUrlFails() {
        // HikariCP will throw an exception when given an invalid JDBC URL.
        assertThrows(RuntimeException.class, () -> {
            Database db = new Database("jdbc:invalid:url:sonion_ring");
            db.close();
        }, "Database instantiation should fail with a bad URL");
    }

    @Test
    void flywayMigrationsWork() {
        try (Database database = new Database("jdbc:h2:mem:test_migrations;DB_CLOSE_DELAY=-1")) {

            // If Flyway didn't run, H2 will throw an exception.
            // basically, this ensures that Flyway created the users table
            int userCount = database.jdbi().withHandle(handle ->
                handle.createQuery("SELECT COUNT(*) FROM users")    // just check how many users
                    .mapTo(int.class)
                    .one()
            );
            
            // there should be 0 users, the table was just created (duh)
            assertEquals(0, userCount, "Flyway should have created a users table");
        }
    }
}

