package dj.redseabunkering.crm.company;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import dj.redseabunkering.crm.common.BaseEntity;

@Entity
@Table(name = "companies")
public class Company extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CompanyType type;

    private String country;

    private String city;

    @Column(name = "fleet_size")
    private Integer fleetSize;

    private String website;

    @Column(name = "owner_id")
    private UUID ownerId;

    protected Company() {
    }

    public Company(String name, CompanyType type, UUID ownerId) {
        this.name = name.trim();
        this.type = type;
        this.ownerId = ownerId;
    }

    public void update(String name, CompanyType type, String country, String city, Integer fleetSize, String website) {
        this.name = name.trim();
        this.type = type;
        this.country = blankToNull(country);
        this.city = blankToNull(city);
        this.fleetSize = fleetSize;
        this.website = blankToNull(website);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    public String getName() {
        return name;
    }

    public CompanyType getType() {
        return type;
    }

    public String getCountry() {
        return country;
    }

    public String getCity() {
        return city;
    }

    public Integer getFleetSize() {
        return fleetSize;
    }

    public String getWebsite() {
        return website;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(UUID ownerId) {
        this.ownerId = ownerId;
    }
}
