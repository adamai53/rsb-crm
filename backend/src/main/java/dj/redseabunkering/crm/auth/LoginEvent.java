package dj.redseabunkering.crm.auth;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Journal des tentatives de connexion (réussies ou non). */
@Entity
@Table(name = "login_events")
public class LoginEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private boolean success;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected LoginEvent() {
    }

    public LoginEvent(UUID userId, String email, boolean success, ClientInfo client, Instant occurredAt) {
        this.userId = userId;
        this.email = email.length() > 254 ? email.substring(0, 254) : email;
        this.success = success;
        this.ipAddress = client.ip();
        this.userAgent = client.userAgent();
        this.occurredAt = occurredAt;
    }
}
