package dj.redseabunkering.crm.activity;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import dj.redseabunkering.crm.common.ApiException;
import dj.redseabunkering.crm.common.CurrentUser;

/**
 * Journal d'activité d'une fiche. Tout utilisateur peut consigner une note, un appel, un e-mail ou une réunion,
 * y compris sur le dossier d'un collègue : c'est un ajout signé par son auteur, pas une modification du dossier.
 */
@RestController
@RequestMapping("/api/activities")
public class ActivityController {

    private final ActivityRepository activities;
    private final ActivityQueries queries;
    private final Clock clock;

    public ActivityController(ActivityRepository activities, ActivityQueries queries, Clock clock) {
        this.activities = activities;
        this.queries = queries;
        this.clock = clock;
    }

    @GetMapping
    public List<ActivityDto> list(@RequestParam(required = false) UUID contactId,
            @RequestParam(required = false) UUID companyId,
            @RequestParam(required = false) UUID dealId,
            @RequestParam(defaultValue = "100") int limit) {
        String column;
        UUID id;
        if (contactId != null) {
            column = "a.contact_id";
            id = contactId;
        } else if (companyId != null) {
            column = "a.company_id";
            id = companyId;
        } else if (dealId != null) {
            column = "a.deal_id";
            id = dealId;
        } else {
            throw ApiException.badRequest("Précisez un contact, une entreprise ou une transaction.");
        }
        return queries.forRecord(column, id, Math.max(1, Math.min(limit, 500)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Map<String, UUID> create(@Valid @RequestBody ActivityRequest req, CurrentUser me) {
        if (req.type() == ActivityType.SYSTEM) {
            throw ApiException.badRequest("Ce type d'activité est réservé au système.");
        }
        if (req.contactId() == null && req.companyId() == null && req.dealId() == null) {
            throw ApiException.badRequest("Précisez un contact, une entreprise ou une transaction.");
        }
        var activity = new Activity(req.type(), req.text().trim(), Instant.now(clock), me.id(), req.dealId(),
                req.contactId(), req.companyId());
        return Map.of("id", activities.save(activity).getId());
    }

    public record ActivityRequest(
            @NotNull(message = "Le type est obligatoire.") ActivityType type,
            @NotBlank(message = "Le texte est obligatoire.") @Size(max = 5000, message = "5 000 caractères maximum.")
            String text,
            UUID contactId,
            UUID companyId,
            UUID dealId) {
    }
}
