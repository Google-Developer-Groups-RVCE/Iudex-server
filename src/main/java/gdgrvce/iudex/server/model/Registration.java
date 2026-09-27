package gdgrvce.iudex.server.model;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "registration")
public class Registration {
    @EmbeddedId
    public RegistrationId regId;

    @MapsId("userId")
    @ManyToOne
    @JoinColumn(name = "user_id")
    User user;

    @MapsId("contestId")
    @ManyToOne
    @JoinColumn(name = "contest_id")
    Contest contest;

    @Column(nullable = false)
    private OffsetDateTime registrationTime;

    public Registration(){}

    public RegistrationId getRegId() {
        return regId;
    }

    public void setRegId(RegistrationId regId) {
        this.regId = regId;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Contest getContest() {
        return contest;
    }

    public void setContest(Contest contest) {
        this.contest = contest;
    }

    public OffsetDateTime getRegistrationTime() {
        return registrationTime;
    }

    public void setRegistrationTime(OffsetDateTime registrationTime) {
        this.registrationTime = registrationTime;
    }
}

