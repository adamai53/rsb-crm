package dj.redseabunkering.crm.sequence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import dj.redseabunkering.crm.common.BaseEntity;

@Entity
@Table(name = "sequences")
public class Sequence extends BaseEntity {

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(name = "auto_send", nullable = false)
    private boolean autoSend;

    @Column(nullable = false)
    private boolean active = true;

    protected Sequence() {
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public boolean isAutoSend() {
        return autoSend;
    }

    public void setAutoSend(boolean autoSend) {
        this.autoSend = autoSend;
    }

    public boolean isActive() {
        return active;
    }
}
