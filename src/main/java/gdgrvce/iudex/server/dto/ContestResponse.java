package gdgrvce.iudex.server.dto;

import gdgrvce.iudex.server.model.Contest;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A contest as returned to any authenticated caller.
 *
 * <p>Carries the window rather than a label derived from it. Whether a contest
 * is upcoming, running, or over follows from {@code startTime} and
 * {@code endTime} against the reader's own clock, and a label computed here
 * would be stale the moment it was serialized.</p>
 */
public record ContestResponse(UUID contestId,
                              String contestName,
                              UUID hostId,
                              OffsetDateTime startTime,
                              OffsetDateTime endTime) {

    public static ContestResponse from(Contest contest) {
        return new ContestResponse(
                contest.getContestId(),
                contest.getContestName(),
                contest.getHostId(),
                contest.getStartTime(),
                contest.getEndTime());
    }
}
