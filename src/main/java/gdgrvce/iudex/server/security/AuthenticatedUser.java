package gdgrvce.iudex.server.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.Collection;

/**
 * Spring Security's user, plus the token version the JWT filter compares
 * against. Loading it with the user saves the filter a second lookup.
 */
public class AuthenticatedUser extends User {
    private final int tokenVersion;

    public AuthenticatedUser(String username,
                             String password,
                             Collection<? extends GrantedAuthority> authorities,
                             int tokenVersion) {
        super(username, password, authorities);
        this.tokenVersion = tokenVersion;
    }

    public int getTokenVersion() {
        return tokenVersion;
    }
}
