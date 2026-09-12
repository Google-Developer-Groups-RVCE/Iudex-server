package gdgrvce.iudex.server.dto;

/** The user details returned by {@code GET /auth/me}. */
public record MeResponse(String username, String role) {
}
