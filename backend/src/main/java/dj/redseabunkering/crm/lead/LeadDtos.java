package dj.redseabunkering.crm.lead;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class LeadDtos {

    private LeadDtos() {
    }

    public record LeadDto(UUID id, String name, String companyName, String email, String phone, String vesselName,
            String imo, String fuelGrade, BigDecimal quantityMt, LocalDate eta, String deliveryLocation,
            String source, String message, LeadStatus status, Instant receivedAt, UUID convertedDealId,
            BigDecimal estimatedValueUsd) {
    }

    /** Saisie manuelle d'une demande (appel, e-mail), ou demande du site une fois contrôlée. */
    public record LeadRequest(
            @NotBlank(message = "Le nom du demandeur est obligatoire.") @Size(max = 160) String name,
            @Size(max = 160) String companyName,
            @Email(message = "Adresse e-mail invalide.") @Size(max = 254) String email,
            @Size(max = 40) String phone,
            @NotBlank(message = "Le nom du navire est obligatoire.") @Size(max = 120) String vesselName,
            @Pattern(regexp = "^$|^[0-9]{7}$", message = "Le numéro IMO compte 7 chiffres.") String imo,
            @NotBlank(message = "Le produit est obligatoire.") String fuelGrade,
            @DecimalMin(value = "0.01", message = "La quantité doit être positive.")
            @DecimalMax(value = "100000", message = "Quantité trop élevée.") BigDecimal quantityMt,
            LocalDate eta,
            String deliveryLocation,
            @Size(max = 40) String source,
            @Size(max = 5000) String message) {
    }

    public record ConversionResult(UUID dealId, UUID contactId, UUID companyId, UUID ownerId, String ownerName,
            boolean companyCreated, boolean contactCreated) {
    }
}
