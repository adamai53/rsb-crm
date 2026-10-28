package dj.redseabunkering.crm.contact;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import dj.redseabunkering.crm.contact.ContactScoring.Score;
import dj.redseabunkering.crm.deal.RelatedRecords.DealSummary;
import dj.redseabunkering.crm.deal.RelatedRecords.TaskSummary;

public final class ContactDtos {

    private ContactDtos() {
    }

    public record ContactRow(UUID id, String firstName, String lastName, String title, String email, String phone,
            UUID companyId, String companyName, Lifecycle lifecycle, String source, UUID ownerId, int score,
            Instant lastActivityAt, Instant createdAt) {
    }

    /** Page de contacts + compteurs par cycle de vie (pastilles de filtre), avec les autres filtres appliqués. */
    public record ContactList(List<ContactRow> content, int page, int size, long totalElements, int totalPages,
            Map<Lifecycle, Long> lifecycleCounts, long allCount) {
    }

    public record ContactDetail(ContactRow contact, Score score, List<DealSummary> deals, List<TaskSummary> tasks,
            boolean editable) {
    }

    public record ContactRequest(
            @NotBlank(message = "Le prénom est obligatoire.") @Size(max = 80, message = "80 caractères maximum.")
            String firstName,
            @NotBlank(message = "Le nom est obligatoire.") @Size(max = 80, message = "80 caractères maximum.")
            String lastName,
            @Size(max = 120, message = "120 caractères maximum.") String title,
            @Email(message = "Adresse e-mail invalide.") @Size(max = 254, message = "Adresse e-mail trop longue.")
            String email,
            @Size(max = 40, message = "40 caractères maximum.") String phone,
            UUID companyId,
            @NotNull(message = "Le cycle de vie est obligatoire.") Lifecycle lifecycle,
            @Size(max = 40, message = "40 caractères maximum.") String source,
            UUID ownerId) {
    }

    /** Modification rapide depuis la fiche (sélecteurs du tiroir). */
    public record ContactPatch(Lifecycle lifecycle, UUID ownerId) {
    }
}
