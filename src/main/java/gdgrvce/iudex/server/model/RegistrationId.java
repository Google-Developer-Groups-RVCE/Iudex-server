package gdgrvce.iudex.server.model;

import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class RegistrationId implements Serializable {
    UUID userId;
    UUID contestId;

    @Override
    public boolean equals(Object obj) {
        if (obj == null) return false;
        if (obj.getClass() != this.getClass()) return false;

        final RegistrationId other = (RegistrationId) obj;
        if (this.userId.equals(other.userId) && this.contestId.equals(other.contestId)) {
            return true;
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, contestId);
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public UUID getContestId() {
        return contestId;
    }

    public void setContestId(UUID contestId) {
        this.contestId = contestId;
    }
}
