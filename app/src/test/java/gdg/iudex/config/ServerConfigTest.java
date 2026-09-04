package gdg.iudex.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 *  class ServerConfigTest
 *
 *  Unit tests for ServerConfig.
 *
 *  Tests:
 *  originsAreSplitAndTrimmed - "a, b" is two origins, not one odd one.
 *  blankOriginsAllowNobody - the safe default when unset.
 *  emptyEntriesAreDropped - a stray comma does not create a blank origin.
 *  singleOriginWorks - the common case.
 *  defaultDatabaseUrlIsRelative - and therefore worth overriding.
 */

class ServerConfigTest {

    @Test
    void originsAreSplitAndTrimmed() {
        assertEquals(
            List.of("http://localhost:5173", "http://localhost:3000"),
            ServerConfig.parseOrigins(
                " http://localhost:5173 , http://localhost:3000 ")
        );
    }

    @Test
    void blankOriginsAllowNobody() {
        assertEquals(List.of(), ServerConfig.parseOrigins(null));
        assertEquals(List.of(), ServerConfig.parseOrigins(""));
        assertEquals(List.of(), ServerConfig.parseOrigins("   "));
    }

    @Test
    void emptyEntriesAreDropped() {
        assertEquals(
            List.of("http://a", "http://b"),
            ServerConfig.parseOrigins("http://a,,  ,http://b,")
        );
    }

    @Test
    void singleOriginWorks() {
        assertEquals(
            List.of("http://localhost:5173"),
            ServerConfig.parseOrigins("http://localhost:5173")
        );
    }

    @Test
    void defaultDatabaseUrlIsRelative() {
        // Relative to the working directory, which is why it is
        // logged at startup and overridable with IUDEX_DB_URL.
        assertTrue(ServerConfig.DEFAULT_DATABASE_URL.contains("./"),
            "The default url is relative, and callers should know it");
    }
}
