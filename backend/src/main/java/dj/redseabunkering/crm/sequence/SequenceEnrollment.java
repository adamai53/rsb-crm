package dj.redseabunkering.crm.sequence;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import dj.redseabunkering.crm.common.BaseEntity;

@Entity
@Table(name = "sequence_enrollments")
public class SequenceEnrollment extends BaseEntity {

    @Column(name = "contact_id", nullable = false)
    private UUID contactId;

    @Column(name = "sequence_id", nullable = false)
    private UUID sequenceId;

    @Column(name = "enrolled_at", nullable = false)
    private Instant enrolledAt;

    @Column(nullable = false)
    private String status = "ACTIVE";

    protected SequenceEnrollment() {
    }

    public SequenceEnrollment(UUID contactId, UUID sequenceId, Instant enrolledAt) {
        this.contactId = contactId;
        this.sequenceId = sequenceId;
        this.enrolledAt = enrolledAt;
    }

    public void stop() {
        status = "ARRETEE";
    }

    public boolean isActive() {
        return "ACTIVE".equals(status);
    }

    public UUID getContactId() {
        return contactId;
    }
}
