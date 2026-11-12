package gdgrvce.iudex.server.dto;

import java.util.UUID;

/**
 * The user details returned by {@code GET /auth/me}. The id is what contests
 * carry as {@code hostId}, so a contestmaster can pick out their own.
 */
public record MeResponse(UUID userId, String username, String role) {
}
