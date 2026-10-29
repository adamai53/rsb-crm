package dj.redseabunkering.crm.deal;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import dj.redseabunkering.crm.deal.RelatedRecords.TaskSummary;

public final class DealDtos {

    private DealDtos() {
    }

    public record DealRow(UUID id, String vesselName, String imo, UUID companyId, String companyName, UUID contactId,
            String contactName, String fuelGrade, BigDecimal quantityMt, BigDecimal priceUsdPerMt, BigDecimal amountUsd,
            String stage, String stageLabel, int probability, boolean won, boolean lost, UUID ownerId, String source,
            LocalDate eta, String deliveryLocation, String deliveryLocationLabel, LocalDate closedAt,
            String lostReason, Instant createdAt) {
    }

    /** Liste + indicateurs calculés sur le même filtre (bandeau « n ouvertes · montant »). */
    public record DealList(List<DealRow> content, Summary summary) {
    }

    public record Summary(long openCount, BigDecimal openAmountUsd, BigDecimal weightedPipelineUsd, long wonCount,
            long lostCount, int conversionRatePct) {
    }

    public record DealDetail(DealRow deal, List<TaskSummary> tasks, boolean editable) {
    }

    public record DealRequest(
            @NotBlank(message = "Le nom du navire est obligatoire.") @Size(max = 120, message = "120 caractères maximum.")
            String vesselName,
            @Pattern(regexp = "^$|^[0-9]{7}$", message = "Le numéro IMO compte 7 chiffres.") String imo,
            UUID companyId,
            UUID contactId,
            @NotBlank(message = "Le produit est obligatoire.") String fuelGrade,
            @NotNull(message = "La quantité est obligatoire.")
            @DecimalMin(value = "0.01", message = "La quantité doit être positive.")
            @DecimalMax(value = "100000", message = "Quantité trop élevée.")
            BigDecimal quantityMt,
            @DecimalMin(value = "0", message = "Le prix ne peut pas être négatif.")
            @DecimalMax(value = "100000", message = "Prix trop élevé.")
            BigDecimal priceUsdPerMt,
            String stage,
            @Size(max = 40) String source,
            LocalDate eta,
            String deliveryLocation,
            @Size(max = 80) String lostReason,
            UUID ownerId) {
    }

    public record StageRequest(@NotBlank(message = "L'étape est obligatoire.") String stage,
            @Size(max = 80) String lostReason) {
    }

    public record OwnerRequest(@NotNull(message = "Le commercial est obligatoire.") UUID ownerId) {
    }
}
