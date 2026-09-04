package gdg.iudex.auth;

import gdg.iudex.models.Role;

public record AuthenticatedUser(
    long userId,
    Role role
) {}