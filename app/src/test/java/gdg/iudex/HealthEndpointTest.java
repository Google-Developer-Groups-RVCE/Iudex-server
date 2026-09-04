package gdg.iudex;

import io.javalin.testtools.JavalinTest;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import gdg.iudex.db.Database;

public class HealthEndpointTest {

    @Test
    public void test() {
        Database database = new Database("jdbc:h2:mem:test_health;DB_CLOSE_DELAY=-1");
        var app = CreateApp.create(database.jdbi()); 

        JavalinTest.test(app, (server, client) -> {
            var response = client.get("/api/health");
            
            assertEquals(200, response.code());
            assertEquals("{ \"status\": \"live\" }", response.body().string());
        });

        database.close();
    }
}