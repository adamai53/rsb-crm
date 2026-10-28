package dj.redseabunkering.crm.company;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import dj.redseabunkering.crm.deal.RelatedRecords.DealSummary;

public final class CompanyDtos {

    private CompanyDtos() {
    }

    /** Ligne de la liste, avec les indicateurs commerciaux (le CA ne compte que les livraisons). */
    public record CompanyRow(UUID id, String name, CompanyType type, String country, String city, Integer fleetSize,
            String website, UUID ownerId, long contactsCount, long openDealsCount, BigDecimal deliveredVolumeMt,
            BigDecimal revenueUsd, Instant createdAt) {
    }

    public record CompanyDetail(CompanyRow company, List<ContactMini> contacts, List<DealSummary> deals,
            boolean editable) {
    }

    public record ContactMini(UUID id, String firstName, String lastName, String title, String lifecycle) {
    }

    public record CompanyRequest(
            @NotBlank(message = "Le nom est obligatoire.") @Size(max = 160, message = "160 caractères maximum.")
            String name,
            @NotNull(message = "Le type est obligatoire.") CompanyType type,
            @Size(max = 80, message = "80 caractères maximum.") String country,
            @Size(max = 80, message = "80 caractères maximum.") String city,
            @PositiveOrZero(message = "La flotte ne peut pas être négative.") @Max(value = 100000) Integer fleetSize,
            @Size(max = 255, message = "255 caractères maximum.") String website,
            UUID ownerId) {
    }

    public record OwnerRequest(@NotNull(message = "Le propriétaire est obligatoire.") UUID ownerId) {
    }
}
