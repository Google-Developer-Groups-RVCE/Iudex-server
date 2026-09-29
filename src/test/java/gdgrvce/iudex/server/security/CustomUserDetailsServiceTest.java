package gdgrvce.iudex.server.security;

import gdgrvce.iudex.server.model.Role;
import gdgrvce.iudex.server.model.User;
import gdgrvce.iudex.server.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The JWT filter trusts the version this service loads, so pin what it returns. */
class CustomUserDetailsServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final CustomUserDetailsService service = new CustomUserDetailsService(userRepository);

    @Test
    void carriesTheStoredTokenVersion() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user("alice", Role.CONTESTANT, 4)));

        AuthenticatedUser loaded = service.loadUserByUsername("alice");

        assertEquals("alice", loaded.getUsername());
        assertEquals("hash", loaded.getPassword());
        assertEquals(4, loaded.getTokenVersion());
    }

    @Test
    void mapsTheRoleToASpringAuthority() {
        when(userRepository.findByUsername("root")).thenReturn(Optional.of(user("root", Role.ADMIN, 0)));

        AuthenticatedUser loaded = service.loadUserByUsername("root");

        assertEquals(1, loaded.getAuthorities().size());
        assertEquals("ROLE_ADMIN", loaded.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .findFirst()
                .orElseThrow());
    }

    @Test
    void unknownUsernameIsRejected() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class, () -> service.loadUserByUsername("ghost"));
    }

    private User user(String username, Role role, int tokenVersion) {
        User user = new User(UUID.randomUUID(), username, "hash", role);
        user.setTokenVersion(tokenVersion);
        return user;
    }
}
