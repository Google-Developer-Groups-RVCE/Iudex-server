package gdgrvce.iudex.server.dto;

import gdgrvce.iudex.server.model.Contest;
import gdgrvce.iudex.server.model.ContestStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

/** A contest as returned to any authenticated caller. */
public record ContestResponse(UUID contestId,
                              String contestName,
                              UUID hostId,
                              OffsetDateTime startTime,
                              OffsetDateTime endTime,
                              ContestStatus status) {

    public static ContestResponse from(Contest contest) {
        return new ContestResponse(
                contest.getContestId(),
                contest.getContestName(),
                contest.getHostId(),
                contest.getStartTime(),
                contest.getEndTime(),
                contest.getStatus());
    }
}
