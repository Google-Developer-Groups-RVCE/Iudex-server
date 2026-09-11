package gdgrvce.iudex.server.dto;

import gdgrvce.iudex.server.model.Role;

/** The details returned after a successful registration or login. */
public record AuthResponse(String token, String username, Role role) {
}
