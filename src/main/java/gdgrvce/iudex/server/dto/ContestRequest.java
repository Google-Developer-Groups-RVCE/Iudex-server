package gdgrvce.iudex.server.dto;

import java.time.OffsetDateTime;

/** The fields a contestmaster supplies when creating or updating a contest. */
public record ContestRequest(String contestName, OffsetDateTime startTime, OffsetDateTime endTime) {
}
