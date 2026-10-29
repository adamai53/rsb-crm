package dj.redseabunkering.crm.deal;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.Immutable;

/** Étape du pipeline (paramétrable en base) : probabilité, ordre, étape gagnée ou perdue. */
@Entity
@Immutable
@Table(name = "pipeline_stages")
public class PipelineStage {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String label;

    @Column(nullable = false)
    private int probability;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "is_won", nullable = false)
    private boolean won;

    @Column(name = "is_lost", nullable = false)
    private boolean lost;

    protected PipelineStage() {
    }

    public boolean isOpen() {
        return !won && !lost;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public int getProbability() {
        return probability;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public boolean isWon() {
        return won;
    }

    public boolean isLost() {
        return lost;
    }
}
