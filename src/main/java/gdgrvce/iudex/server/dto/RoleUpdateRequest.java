package gdgrvce.iudex.server.dto;

import gdgrvce.iudex.server.model.Role;

/** The role an administrator is moving an account to. */
public record RoleUpdateRequest(Role role) {
}
