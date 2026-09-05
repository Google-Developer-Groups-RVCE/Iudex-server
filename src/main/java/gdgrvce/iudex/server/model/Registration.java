package gdgrvce.iudex.server.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "registration")
public class Registration {
    @EmbeddedId
    public RegistrationId regId;

    @MapsId("userId")
    @ManyToOne User user;

    @MapsId("contestId")
    @ManyToOne Contest contest;

    @Column(nullable = false)
    private LocalDateTime registrationTime;

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

    public LocalDateTime getRegistrationTime() {
        return registrationTime;
    }

    public void setRegistrationTime(LocalDateTime registrationTime) {
        this.registrationTime = registrationTime;
    }
}

