package dj.redseabunkering.crm.campaign;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import dj.redseabunkering.crm.common.BaseEntity;

/** Campagne marketing : seuls la dépense et l'audience sont saisies, le reste est calculé depuis les sources. */
@Entity
@Table(name = "campaigns")
public class Campaign extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String channel;

    @Column(name = "spend_usd", nullable = false)
    private BigDecimal spendUsd;

    @Column(nullable = false)
    private long impressions;

    @Column(nullable = false)
    private long clicks;

    @Column(name = "period_start")
    private LocalDate periodStart;

    @Column(name = "period_end")
    private LocalDate periodEnd;

    protected Campaign() {
    }

    public Campaign(String name, String channel, BigDecimal spendUsd, long impressions, long clicks,
            LocalDate periodStart, LocalDate periodEnd) {
        update(name, channel, spendUsd, impressions, clicks, periodStart, periodEnd);
    }

    public final void update(String name, String channel, BigDecimal spendUsd, long impressions, long clicks,
            LocalDate periodStart, LocalDate periodEnd) {
        this.name = name.trim();
        this.channel = channel;
        this.spendUsd = spendUsd;
        this.impressions = impressions;
        this.clicks = clicks;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
    }
}
