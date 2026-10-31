package dj.redseabunkering.crm.campaign;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class MarketingDtos {

    private MarketingDtos() {
    }

    public record CampaignRequest(
            @NotBlank(message = "Le nom de la campagne est obligatoire.") @Size(max = 160) String name,
            @NotBlank(message = "Le canal est obligatoire.") String channel,
            @NotNull(message = "La dépense est obligatoire.")
            @DecimalMin(value = "0", message = "La dépense ne peut pas être négative.")
            @DecimalMax(value = "9999999999", message = "Dépense trop élevée.") BigDecimal spendUsd,
            @Min(value = 0, message = "Valeur négative.") long impressions,
            @Min(value = 0, message = "Valeur négative.") long clicks,
            LocalDate periodStart,
            LocalDate periodEnd) {
    }

    /** {@code costPerLeadUsd} et {@code revenuePerUsd} sont nuls quand le calcul n'a pas de sens (division par zéro). */
    public record CampaignRow(UUID id, String name, String channel, BigDecimal spendUsd, long impressions, long clicks,
            LocalDate periodStart, LocalDate periodEnd, long leads, long won, BigDecimal revenueUsd,
            BigDecimal costPerLeadUsd, BigDecimal revenuePerUsd) {
    }

    public record ChannelRow(String channel, BigDecimal spendUsd, long leads, long won, BigDecimal revenueUsd,
            BigDecimal costPerLeadUsd, BigDecimal revenuePerUsd) {
    }

    public record Totals(BigDecimal spendUsd, long leads, long won, BigDecimal revenueUsd, BigDecimal costPerLeadUsd,
            BigDecimal revenuePerUsd, int leadToClientPct) {
    }

    public record Summary(Totals totals, List<CampaignRow> campaigns, List<ChannelRow> channels) {
    }

    /** Réglages du formulaire du site ; la clé est visible dans le code de la page qui l'intègre. */
    public record FormConfig(String endpoint, String apiKey, String captchaSiteKey, boolean captchaEnabled,
            List<String> allowedOrigins) {
    }

    /** Demande envoyée par le formulaire du site. {@code website} est un champ piège, invisible pour un humain. */
    public record PublicLeadRequest(
            @NotBlank(message = "Le nom est obligatoire.") @Size(max = 160) String name,
            @NotBlank(message = "La société est obligatoire.") @Size(max = 160) String company,
            @NotBlank(message = "L'e-mail est obligatoire.")
            @Email(message = "Adresse e-mail invalide.") @Size(max = 254) String email,
            @Size(max = 40) String phone,
            @NotBlank(message = "Le nom du navire est obligatoire.") @Size(max = 120) String vessel,
            @Pattern(regexp = "^$|^[0-9]{7}$", message = "Le numéro IMO compte 7 chiffres.") String imo,
            @NotBlank(message = "Le produit est obligatoire.") String fuelGrade,
            @NotNull(message = "La quantité est obligatoire.")
            @DecimalMin(value = "0.01", message = "La quantité doit être positive.")
            @DecimalMax(value = "100000", message = "Quantité trop élevée.") BigDecimal quantityMt,
            LocalDate eta,
            @Size(max = 40) String source,
            @Size(max = 5000) String message,
            @Size(max = 4096) String captchaToken,
            @Size(max = 200) String website) {
    }
}
