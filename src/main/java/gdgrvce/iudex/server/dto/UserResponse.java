package gdgrvce.iudex.server.dto;

import gdgrvce.iudex.server.model.Role;

import java.util.UUID;

/** An account as listed to an administrator. Carries no password material. */
public record UserResponse(UUID userId, String username, Role role) {
}
