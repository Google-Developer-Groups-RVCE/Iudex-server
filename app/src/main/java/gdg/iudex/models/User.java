package gdg.iudex.models;

import java.time.OffsetDateTime;

public record User(
    long id,
    String username,
    String passwordHash,
    Role role,
    OffsetDateTime createdAt
) {}