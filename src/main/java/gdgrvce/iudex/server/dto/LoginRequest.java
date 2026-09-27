package gdgrvce.iudex.server.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * The username and password sent when someone logs in.
 *
 * <p>Only checked for presence. Login deliberately does not apply the
 * registration rules: an account created under earlier rules must still be able
 * to sign in, and rejecting a password here for being too short would describe
 * the policy to someone guessing.</p>
 */
public record LoginRequest(
        @NotBlank(message = "username must not be blank") String username,
        @NotBlank(message = "password must not be blank") String password) {
}
