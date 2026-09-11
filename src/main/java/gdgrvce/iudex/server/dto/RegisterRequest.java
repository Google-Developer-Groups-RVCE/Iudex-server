package gdgrvce.iudex.server.dto;

/** The username and password needed to create a new account. */
public record RegisterRequest(String username, String password) {
}
