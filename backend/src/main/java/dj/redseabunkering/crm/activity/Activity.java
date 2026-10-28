package dj.redseabunkering.crm.activity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import dj.redseabunkering.crm.common.BaseEntity;

@Entity
@Table(name = "activities")
public class Activity extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ActivityType type;

    @Column(nullable = false)
    private String text;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "author_id")
    private UUID authorId;

    @Column(name = "deal_id")
    private UUID dealId;

    @Column(name = "contact_id")
    private UUID contactId;

    @Column(name = "company_id")
    private UUID companyId;

    protected Activity() {
    }

    public Activity(ActivityType type, String text, Instant occurredAt, UUID authorId, UUID dealId, UUID contactId,
            UUID companyId) {
        this.type = type;
        this.text = text;
        this.occurredAt = occurredAt;
        this.authorId = authorId;
        this.dealId = dealId;
        this.contactId = contactId;
        this.companyId = companyId;
    }
}
