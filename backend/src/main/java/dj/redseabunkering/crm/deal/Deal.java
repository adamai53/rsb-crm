package dj.redseabunkering.crm.deal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import dj.redseabunkering.crm.common.BaseEntity;

/**
 * Transaction = une escale de soutage. Le montant (quantité × prix) est une colonne générée par PostgreSQL :
 * il n'est pas mappé ici et se lit via {@link DealQueries}.
 */
@Entity
@Table(name = "deals")
public class Deal extends BaseEntity {

    @Column(name = "vessel_name", nullable = false)
    private String vesselName;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 7)
    private String imo;

    @Column(name = "company_id")
    private UUID companyId;

    @Column(name = "contact_id")
    private UUID contactId;

    @Column(name = "fuel_grade", nullable = false)
    private String fuelGrade;

    @Column(name = "quantity_mt", nullable = false)
    private BigDecimal quantityMt;

    @Column(name = "price_usd_per_mt", nullable = false)
    private BigDecimal priceUsdPerMt;

    @Column(nullable = false)
    private String stage;

    @Column(name = "owner_id")
    private UUID ownerId;

    private String source;

    private LocalDate eta;

    @Column(name = "delivery_location")
    private String deliveryLocation;

    @Column(name = "closed_at")
    private LocalDate closedAt;

    @Column(name = "lost_reason")
    private String lostReason;

    protected Deal() {
    }

    public Deal(String vesselName, String fuelGrade, BigDecimal quantityMt, BigDecimal priceUsdPerMt, String stage,
            UUID ownerId) {
        this.vesselName = vesselName.trim();
        this.fuelGrade = fuelGrade;
        this.quantityMt = quantityMt;
        this.priceUsdPerMt = priceUsdPerMt;
        this.stage = stage;
        this.ownerId = ownerId;
    }

    /** Champs descriptifs de l'escale ; l'étape se change uniquement par {@link DealService#changeStage}. */
    public void describe(String vesselName, String imo, UUID companyId, UUID contactId, String fuelGrade,
            BigDecimal quantityMt, BigDecimal priceUsdPerMt, String source, LocalDate eta, String deliveryLocation) {
        this.vesselName = vesselName.trim();
        this.imo = imo == null || imo.isBlank() ? null : imo.trim();
        this.companyId = companyId;
        this.contactId = contactId;
        this.fuelGrade = fuelGrade;
        this.quantityMt = quantityMt;
        this.priceUsdPerMt = priceUsdPerMt;
        this.source = source == null || source.isBlank() ? null : source.trim();
        this.eta = eta;
        this.deliveryLocation = deliveryLocation;
    }

    /** Règles 4 à 6 côté transaction : date de clôture et motif de perte suivent l'étape. */
    void moveTo(PipelineStage target, String lostReason, LocalDate today) {
        this.stage = target.getCode();
        if (target.isWon()) {
            this.closedAt = today;
            this.lostReason = null;
        } else if (target.isLost()) {
            this.closedAt = today;
            this.lostReason = lostReason;
        } else {
            this.closedAt = null;
            this.lostReason = null;
        }
    }

    public void setPrice(BigDecimal priceUsdPerMt) {
        this.priceUsdPerMt = priceUsdPerMt;
    }

    public String title() {
        return vesselName + " · " + fuelGrade + " " + quantityMt.stripTrailingZeros().toPlainString() + " MT";
    }

    public String getVesselName() {
        return vesselName;
    }

    public String getImo() {
        return imo;
    }

    public UUID getCompanyId() {
        return companyId;
    }

    public UUID getContactId() {
        return contactId;
    }

    public String getFuelGrade() {
        return fuelGrade;
    }

    public BigDecimal getQuantityMt() {
        return quantityMt;
    }

    public BigDecimal getPriceUsdPerMt() {
        return priceUsdPerMt;
    }

    public String getStage() {
        return stage;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(UUID ownerId) {
        this.ownerId = ownerId;
    }

    public String getSource() {
        return source;
    }

    public LocalDate getEta() {
        return eta;
    }

    public String getDeliveryLocation() {
        return deliveryLocation;
    }

    public LocalDate getClosedAt() {
        return closedAt;
    }

    public String getLostReason() {
        return lostReason;
    }
}
