package gdg.iudex;

import io.javalin.testtools.JavalinTest;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class HealthEndpointTest {

    @Test
    public void test() {
        // this function doesn't need a jdbi instance
        // because it doesn't touch the database
        var app = CreateApp.create(null); 

        JavalinTest.test(app, (server, client) -> {
            var response = client.get("/api/health");
            
            assertEquals(200, response.code());
            assertEquals("{ \"status\": \"live\" }", response.body().string());
        });
    }
}