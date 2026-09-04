package gdg.iudex.auth;

import gdg.iudex.models.Role;

import java.time.Instant;

/**
 *  record AuthenticatedUser
 *
 *  Everything the server learned by verifying a token, so that no
 *  layer above has to parse or re-verify the token a second time.
 *
 *  @tokenId - the token's unique id (its "jti"), needed to revoke it
 *  @expiresAt - when the token expires, needed to age out its
 *               revocation record once it can no longer be used
 */

public record AuthenticatedUser(
    long userId,
    Role role,
    String tokenId,
    Instant expiresAt
) {}
