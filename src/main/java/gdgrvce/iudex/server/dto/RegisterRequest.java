package gdgrvce.iudex.server.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * The username and password needed to create a new account.
 *
 * <p>Both are constrained here rather than left to the database. A blank
 * username used to be accepted, and the account it made could never sign in
 * again: an empty JWT subject is dropped from the token, so nothing could
 * identify the user, and the unique constraint stopped anyone re-registering
 * the name to clear it.</p>
 */
public record RegisterRequest(
        @NotBlank(message = "username must not be blank")
        @Size(min = 3, max = 50, message = "username must be between 3 and 50 characters")
        @Pattern(regexp = "^[A-Za-z0-9._-]+$",
                message = "username may contain only letters, digits, dot, underscore and hyphen")
        String username,

        // BCrypt reads at most 72 bytes, so anything beyond that is not password.
        @NotBlank(message = "password must not be blank")
        @Size(min = 8, max = 72, message = "password must be between 8 and 72 characters")
        String password) {
}
