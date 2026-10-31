package dj.redseabunkering.crm.email;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import dj.redseabunkering.crm.common.BaseEntity;

/** Historique des envois ; statut ENVOYE, OUVERT (pixel de suivi) ou ERREUR (échec SMTP). */
@Entity
@Table(name = "sent_emails")
public class SentEmail extends BaseEntity {

    public static final String SENT = "ENVOYE";
    public static final String OPENED = "OUVERT";
    public static final String ERROR = "ERREUR";

    @Column(name = "to_contact_id")
    private UUID toContactId;

    @Column(name = "to_address", nullable = false)
    private String toAddress;

    @Column(name = "deal_id")
    private UUID dealId;

    @Column(name = "quote_id")
    private UUID quoteId;

    @Column(nullable = false)
    private String subject;

    @Column(nullable = false)
    private String body;

    @Column(nullable = false)
    private String status = SENT;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    @Column(name = "opened_at")
    private Instant openedAt;

    @Column(name = "tracking_id", nullable = false)
    private UUID trackingId;

    protected SentEmail() {
    }

    public SentEmail(UUID toContactId, String toAddress, UUID dealId, UUID quoteId, String subject, String body,
            Instant sentAt, UUID trackingId) {
        this.toContactId = toContactId;
        this.toAddress = toAddress;
        this.dealId = dealId;
        this.quoteId = quoteId;
        this.subject = subject;
        this.body = body;
        this.sentAt = sentAt;
        this.trackingId = trackingId;
    }

    public void markFailed(String message) {
        status = ERROR;
        errorMessage = message == null || message.length() <= 500 ? message : message.substring(0, 500);
    }

    /** Seule la première ouverture compte ; un envoi en erreur ne peut pas être « ouvert ». */
    public void markOpened(Instant now) {
        if (SENT.equals(status)) {
            status = OPENED;
            openedAt = now;
        }
    }
}
