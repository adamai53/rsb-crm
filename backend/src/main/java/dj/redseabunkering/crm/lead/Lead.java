package dj.redseabunkering.crm.lead;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import dj.redseabunkering.crm.common.BaseEntity;

/** Demande entrante (formulaire du site, campagne, appel, e-mail) en attente de qualification. */
@Entity
@Table(name = "leads")
public class Lead extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(name = "company_name")
    private String companyName;

    private String email;

    private String phone;

    @Column(name = "vessel_name")
    private String vesselName;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 7)
    private String imo;

    @Column(name = "fuel_grade")
    private String fuelGrade;

    @Column(name = "quantity_mt")
    private BigDecimal quantityMt;

    private LocalDate eta;

    @Column(name = "delivery_location")
    private String deliveryLocation;

    private String source;

    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LeadStatus status = LeadStatus.NOUVEAU;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "converted_deal_id")
    private UUID convertedDealId;

    protected Lead() {
    }

    public Lead(String name, Instant receivedAt) {
        this.name = name.trim();
        this.receivedAt = receivedAt;
    }

    public void describe(String companyName, String email, String phone, String vesselName, String imo,
            String fuelGrade, BigDecimal quantityMt, LocalDate eta, String deliveryLocation, String source,
            String message) {
        this.companyName = clean(companyName);
        this.email = clean(email) == null ? null : email.trim().toLowerCase(Locale.ROOT);
        this.phone = clean(phone);
        this.vesselName = clean(vesselName);
        this.imo = clean(imo);
        this.fuelGrade = clean(fuelGrade);
        this.quantityMt = quantityMt;
        this.eta = eta;
        this.deliveryLocation = clean(deliveryLocation);
        this.source = clean(source);
        this.message = clean(message);
    }

    private static String clean(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    void markConverted(UUID dealId) {
        this.status = LeadStatus.CONVERTI;
        this.convertedDealId = dealId;
    }

    void markRejected() {
        this.status = LeadStatus.REJETE;
    }

    public String getName() {
        return name;
    }

    public String getCompanyName() {
        return companyName;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public String getVesselName() {
        return vesselName;
    }

    public String getImo() {
        return imo;
    }

    public String getFuelGrade() {
        return fuelGrade;
    }

    public BigDecimal getQuantityMt() {
        return quantityMt;
    }

    public LocalDate getEta() {
        return eta;
    }

    public String getDeliveryLocation() {
        return deliveryLocation;
    }

    public String getSource() {
        return source;
    }

    public String getMessage() {
        return message;
    }

    public LeadStatus getStatus() {
        return status;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public UUID getConvertedDealId() {
        return convertedDealId;
    }
}
