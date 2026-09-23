package gdgrvce.iudex.server.model;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "contest")
public class Contest {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID contestId;

    @Column(nullable = false)
    private String contestName;

    // TIMESTAMP WITH TIME ZONE: the server clock is authoritative for the window.
    @Column(nullable = false)
    private OffsetDateTime startTime;

    @Column(nullable = false)
    private OffsetDateTime endTime;

    @Column(nullable = false)
    private UUID hostId;

    // dont store this in DB
    @Transient
    private ContestStatus status;

    public Contest(){}

    // calculate contest status dynamically
    public ContestStatus getStatus() {
        OffsetDateTime now = OffsetDateTime.now();
        if (now.isBefore(startTime)) {
            return ContestStatus.UPCOMING;
        } else if(now.isAfter(endTime.plusHours(24))) {
            return ContestStatus.ARCHIVED;
        } else if (now.isAfter(endTime)) {
            return ContestStatus.FINISHED;
        } else {
            return ContestStatus.ONGOING;
        }
    }

    public UUID getContestId() {
        return contestId;
    }

    public void setContestId(UUID contestId) {
        this.contestId = contestId;
    }

    public String getContestName() {
        return contestName;
    }

    public void setContestName(String contestName) {
        this.contestName = contestName;
    }

    public OffsetDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(OffsetDateTime startTime) {
        this.startTime = startTime;
    }

    public OffsetDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(OffsetDateTime endTime) {
        this.endTime = endTime;
    }

    public UUID getHostId() {
        return hostId;
    }

    public void setHostId(UUID hostId) {
        this.hostId = hostId;
    }
}
