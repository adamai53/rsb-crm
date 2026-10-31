package dj.redseabunkering.crm.quote;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import dj.redseabunkering.crm.common.BaseEntity;

@Entity
@Table(name = "quotes")
public class Quote extends BaseEntity {

    @Column(nullable = false)
    private String number;

    @Column(name = "deal_id", nullable = false)
    private UUID dealId;

    @Column(name = "price_usd_per_mt", nullable = false)
    private BigDecimal priceUsdPerMt;

    @Column(name = "barge_fee_usd", nullable = false)
    private BigDecimal bargeFeeUsd;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QuoteStatus status = QuoteStatus.BROUILLON;

    @Column(name = "valid_until", nullable = false)
    private Instant validUntil;

    @Column(name = "sent_at")
    private Instant sentAt;

    protected Quote() {
    }

    public Quote(String number, UUID dealId, BigDecimal priceUsdPerMt, BigDecimal bargeFeeUsd, Instant validUntil) {
        this.number = number;
        this.dealId = dealId;
        this.priceUsdPerMt = priceUsdPerMt;
        this.bargeFeeUsd = bargeFeeUsd;
        this.validUntil = validUntil;
    }

    public void markSent(Instant now) {
        status = QuoteStatus.ENVOYE;
        sentAt = now;
    }

    public void setStatus(QuoteStatus status) {
        this.status = status;
    }

    public String getNumber() {
        return number;
    }

    public UUID getDealId() {
        return dealId;
    }

    public BigDecimal getPriceUsdPerMt() {
        return priceUsdPerMt;
    }

    public BigDecimal getBargeFeeUsd() {
        return bargeFeeUsd;
    }

    public QuoteStatus getStatus() {
        return status;
    }

    public Instant getValidUntil() {
        return validUntil;
    }
}
