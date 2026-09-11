package gdgrvce.iudex.server.dto;

/** The username and password sent when someone logs in. */
public record LoginRequest(String username, String password) {
}
