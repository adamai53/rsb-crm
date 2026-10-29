package dj.redseabunkering.crm.task;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import dj.redseabunkering.crm.common.BaseEntity;

@Entity
@Table(name = "tasks")
public class Task extends BaseEntity {

    @Column(nullable = false)
    private String title;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskPriority priority;

    @Column(name = "owner_id")
    private UUID ownerId;

    @Column(nullable = false)
    private boolean done;

    @Column(name = "done_at")
    private Instant doneAt;

    @Column(name = "deal_id")
    private UUID dealId;

    @Column(name = "contact_id")
    private UUID contactId;

    @Column(name = "company_id")
    private UUID companyId;

    protected Task() {
    }

    public Task(String title, LocalDate dueDate, TaskPriority priority, UUID ownerId, UUID dealId, UUID contactId,
            UUID companyId) {
        this.title = title.trim();
        this.dueDate = dueDate;
        this.priority = priority;
        this.ownerId = ownerId;
        this.dealId = dealId;
        this.contactId = contactId;
        this.companyId = companyId;
    }

    public void update(String title, LocalDate dueDate, TaskPriority priority) {
        this.title = title.trim();
        this.dueDate = dueDate;
        this.priority = priority;
    }

    public void setDone(boolean done, Instant now) {
        this.done = done;
        this.doneAt = done ? now : null;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(UUID ownerId) {
        this.ownerId = ownerId;
    }

    public boolean isDone() {
        return done;
    }
}
