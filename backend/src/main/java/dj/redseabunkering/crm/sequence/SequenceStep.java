package dj.redseabunkering.crm.sequence;

import java.util.Locale;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import dj.redseabunkering.crm.common.BaseEntity;

@Entity
@Table(name = "sequence_steps")
public class SequenceStep extends BaseEntity {

    @Column(name = "sequence_id", nullable = false)
    private UUID sequenceId;

    @Column(name = "day_offset", nullable = false)
    private int dayOffset;

    @Column(nullable = false)
    private String type;

    @Column(nullable = false)
    private String label;

    @Column(name = "email_template_id")
    private UUID emailTemplateId;

    protected SequenceStep() {
    }

    /** Intitulé de la tâche créée : « E-mail : envoi de cotation », « Appel : point sur la décision »… */
    public String taskTitle() {
        String lower = label.substring(0, 1).toLowerCase(Locale.FRENCH) + label.substring(1);
        return switch (type) {
            case "EMAIL" -> "E-mail : " + lower;
            case "CALL" -> "Appel : " + lower;
            default -> label;
        };
    }

    public int getDayOffset() {
        return dayOffset;
    }

    public String getType() {
        return type;
    }

    public String getLabel() {
        return label;
    }

    public UUID getEmailTemplateId() {
        return emailTemplateId;
    }
}
