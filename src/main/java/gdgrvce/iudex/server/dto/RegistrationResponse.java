package gdgrvce.iudex.server.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/** A participant registered for a contest. */
public record RegistrationResponse(UUID contestId, UUID userId, String username, OffsetDateTime registrationTime) {
}
