package gdg.iudex.auth;

import gdg.iudex.models.Role;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 *  class AccessTest
 *
 *  Unit tests for Access.
 *
 *  Access mirrors Role by name, and these pin that relationship down
 *  so it cannot quietly drift as roles are added.
 *
 *  Tests:
 *  everyRoleHasAnAccess - the mirror is complete.
 *  ofMapsRoleToMatchingAccess - the mapping is by name.
 *  authenticatedIsEveryRoleButPublic - and never includes PUBLIC.
 *  publicIsNotARole - PUBLIC must not map back to a Role.
 */

class AccessTest {

    @Test
    void everyRoleHasAnAccess() {
        for (Role role : Role.values()) {
            assertDoesNotThrow(() -> Access.of(role),
                "Role." + role + " has no matching Access");
        }
    }

    @Test
    void ofMapsRoleToMatchingAccess() {
        assertEquals(Access.CONTESTANT, Access.of(Role.CONTESTANT));
        assertEquals(Access.CONTESTMASTER, Access.of(Role.CONTESTMASTER));
        assertEquals(Access.ADMINISTRATOR, Access.of(Role.ADMINISTRATOR));
    }

    @Test
    void authenticatedIsEveryRoleButPublic() {
        List<Access> authenticated = Arrays.asList(Access.authenticated());

        assertFalse(authenticated.contains(Access.PUBLIC),
            "PUBLIC must never be handed out as a signed-in role");

        assertEquals(Role.values().length, authenticated.size(),
            "Every role should count as authenticated");

        for (Role role : Role.values()) {
            assertTrue(authenticated.contains(Access.of(role)));
        }
    }

    @Test
    void publicIsNotARole() {
        assertThrows(IllegalArgumentException.class,
            () -> Role.valueOf(Access.PUBLIC.name()),
            "PUBLIC is a route marker, not something a user can be");
    }
}
