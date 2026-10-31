package dj.redseabunkering.crm.quote;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class QuoteDtos {

    private QuoteDtos() {
    }

    public record QuoteRow(UUID id, String number, UUID dealId, String vesselName, String imo, UUID companyId,
            String companyName, UUID contactId, String contactName, String contactEmail, String fuelGrade,
            String fuelGradeLabel, BigDecimal quantityMt, BigDecimal priceUsdPerMt, BigDecimal bargeFeeUsd,
            BigDecimal productAmountUsd, BigDecimal totalUsd, QuoteStatus status, Instant validUntil, Instant sentAt,
            Instant createdAt, LocalDate eta, String deliveryLocationLabel, UUID ownerId, String ownerName) {
    }

    public record QuoteRequest(
            @NotNull(message = "La transaction est obligatoire.") UUID dealId,
            @DecimalMin(value = "0", message = "Le prix ne peut pas être négatif.")
            @DecimalMax(value = "100000", message = "Prix trop élevé.") BigDecimal priceUsdPerMt,
            @DecimalMin(value = "0", message = "Les frais ne peuvent pas être négatifs.")
            @DecimalMax(value = "1000000", message = "Frais trop élevés.") BigDecimal bargeFeeUsd) {
    }

    public record QuoteEmail(
            @NotBlank(message = "L'objet est obligatoire.") @Size(max = 255, message = "255 caractères maximum.")
            String subject,
            @NotBlank(message = "Le message est obligatoire.") @Size(max = 20000, message = "Message trop long.")
            String body) {
    }
}
