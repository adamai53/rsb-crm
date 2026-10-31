package dj.redseabunkering.crm.email;

import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import dj.redseabunkering.crm.common.ApiException;
import dj.redseabunkering.crm.common.CurrentUser;
import dj.redseabunkering.crm.email.EmailService.Delivery;
import dj.redseabunkering.crm.email.EmailService.Draft;

/** Modèles (écriture ADMIN), brouillons, envoi, historique et pixel de suivi d'ouverture. */
@RestController
public class EmailController {

    /** PNG transparent 1×1 renvoyé quel que soit le jeton, pour ne rien révéler. */
    private static final byte[] PIXEL = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==");

    private final EmailService emails;
    private final EmailTemplateRepository templates;
    private final NamedParameterJdbcTemplate jdbc;

    public EmailController(EmailService emails, EmailTemplateRepository templates, NamedParameterJdbcTemplate jdbc) {
        this.emails = emails;
        this.templates = templates;
        this.jdbc = jdbc;
    }

    // ------------------------------------------------------------------ modèles

    @GetMapping("/api/email-templates")
    public List<TemplateDto> templates() {
        return templates.findAllByOrderByName().stream().map(EmailController::dto).toList();
    }

    @PostMapping("/api/email-templates")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public TemplateDto createTemplate(@Valid @RequestBody TemplateRequest req) {
        return dto(templates.save(new EmailTemplate(req.name(), req.subject(), req.body())));
    }

    @PutMapping("/api/email-templates/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public TemplateDto updateTemplate(@PathVariable UUID id, @Valid @RequestBody TemplateRequest req) {
        EmailTemplate t = templates.findById(id).orElseThrow(() -> ApiException.notFound("Modèle introuvable."));
        t.update(req.name(), req.subject(), req.body());
        return dto(t);
    }

    @DeleteMapping("/api/email-templates/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void deleteTemplate(@PathVariable UUID id) {
        templates.deleteById(id);
    }

    // ------------------------------------------------------------------ envoi

    @GetMapping("/api/emails/draft")
    public Draft draft(@RequestParam UUID templateId, @RequestParam(required = false) UUID contactId,
            @RequestParam(required = false) UUID dealId) {
        return emails.draft(templateId, contactId, dealId);
    }

    @PostMapping("/api/emails/send")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void send(@Valid @RequestBody SendRequest req, CurrentUser me) {
        emails.deliver(new Delivery(req.contactId(), req.dealId(), null, req.subject().trim(), req.body(), me.id(),
                null, null));
    }

    @GetMapping("/api/emails/sent")
    public List<SentDto> sent(@RequestParam(required = false) UUID contactId,
            @RequestParam(defaultValue = "20") int limit) {
        String where = contactId == null ? "" : " where e.to_contact_id = :contact";
        var params = new HashMap<String, Object>(Map.of("limit", Math.max(1, Math.min(limit, 200))));
        if (contactId != null) {
            params.put("contact", contactId);
        }
        return jdbc.query("""
                select e.id, e.subject, e.to_address, e.to_contact_id,
                       c.first_name || ' ' || c.last_name as contact_name, e.status, e.sent_at, e.opened_at
                from sent_emails e left join contacts c on c.id = e.to_contact_id""" + where
                + " order by e.sent_at desc limit :limit", params, (rs, i) -> {
                    var opened = rs.getTimestamp("opened_at");
                    return new SentDto(rs.getObject("id", UUID.class), rs.getString("subject"),
                            rs.getString("to_address"), rs.getObject("to_contact_id", UUID.class),
                            rs.getString("contact_name"), rs.getString("status"),
                            rs.getTimestamp("sent_at").toInstant(), opened == null ? null : opened.toInstant());
                });
    }

    // ------------------------------------------------------------------ pixel de suivi (public)

    @GetMapping("/api/public/t/{trackingId}.png")
    public ResponseEntity<byte[]> pixel(@PathVariable String trackingId) {
        try {
            emails.trackOpen(UUID.fromString(trackingId));
        } catch (IllegalArgumentException ignored) {
            // Jeton mal formé : même réponse qu'un jeton inconnu
        }
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).cacheControl(CacheControl.noStore()).body(PIXEL);
    }

    private static TemplateDto dto(EmailTemplate t) {
        return new TemplateDto(t.getId(), t.getName(), t.getSubject(), t.getBody());
    }

    public record TemplateDto(UUID id, String name, String subject, String body) {
    }

    public record TemplateRequest(
            @NotBlank(message = "Le nom est obligatoire.") @Size(max = 120) String name,
            @NotBlank(message = "L'objet est obligatoire.") @Size(max = 255) String subject,
            @NotBlank(message = "Le message est obligatoire.") @Size(max = 20000) String body) {
    }

    public record SendRequest(
            @NotNull(message = "Le destinataire est obligatoire.") UUID contactId,
            UUID dealId,
            @NotBlank(message = "L'objet est obligatoire.") @Size(max = 255, message = "255 caractères maximum.")
            String subject,
            @NotBlank(message = "Le message est obligatoire.") @Size(max = 20000, message = "Message trop long.")
            String body) {
    }

    public record SentDto(UUID id, String subject, String toAddress, UUID contactId, String contactName, String status,
            Instant sentAt, Instant openedAt) {
    }
}
