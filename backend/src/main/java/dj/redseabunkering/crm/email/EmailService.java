package dj.redseabunkering.crm.email;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

import dj.redseabunkering.crm.activity.Activity;
import dj.redseabunkering.crm.activity.ActivityRepository;
import dj.redseabunkering.crm.activity.ActivityType;
import dj.redseabunkering.crm.common.ApiException;
import dj.redseabunkering.crm.common.AppProperties;
import dj.redseabunkering.crm.common.FrenchFormat;
import dj.redseabunkering.crm.company.CompanyRepository;
import dj.redseabunkering.crm.contact.Contact;
import dj.redseabunkering.crm.contact.ContactRepository;
import dj.redseabunkering.crm.deal.Deal;
import dj.redseabunkering.crm.deal.DealRepository;
import dj.redseabunkering.crm.user.UserRepository;

/** Envoi réel des e-mails (SMTP), historique, activité sur la fiche, brouillons à partir des modèles. */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender sender;
    private final AppProperties app;
    private final SentEmailRepository sent;
    private final EmailTemplateRepository templates;
    private final ActivityRepository activities;
    private final ContactRepository contacts;
    private final CompanyRepository companies;
    private final DealRepository deals;
    private final UserRepository users;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public EmailService(JavaMailSender sender, AppProperties app, SentEmailRepository sent,
            EmailTemplateRepository templates, ActivityRepository activities, ContactRepository contacts,
            CompanyRepository companies, DealRepository deals, UserRepository users, JdbcTemplate jdbc, Clock clock) {
        this.sender = sender;
        this.app = app;
        this.sent = sent;
        this.templates = templates;
        this.activities = activities;
        this.contacts = contacts;
        this.companies = companies;
        this.deals = deals;
        this.users = users;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** Sans transaction précisée, on reprend la première transaction ouverte du contact (ETA la plus proche). */
    @Transactional(readOnly = true)
    public Draft draft(UUID templateId, UUID contactId, UUID dealId) {
        EmailTemplate template = templates.findById(templateId)
                .orElseThrow(() -> ApiException.notFound("Modèle introuvable."));
        return draft(template, contactId, dealId);
    }

    @Transactional(readOnly = true)
    public Draft draftFromTemplateNamed(String name, UUID contactId, UUID dealId) {
        EmailTemplate template = templates.findByName(name)
                .orElseThrow(() -> ApiException.notFound("Modèle « " + name + " » introuvable."));
        return draft(template, contactId, dealId);
    }

    private Draft draft(EmailTemplate template, UUID contactId, UUID dealId) {
        Contact contact = contactId == null ? null : contacts.findById(contactId)
                .orElseThrow(() -> ApiException.badRequest("Contact introuvable."));
        UUID effectiveDeal = dealId != null ? dealId : contactId == null ? null : firstOpenDeal(contactId);
        Deal deal = effectiveDeal == null ? null : deals.findById(effectiveDeal)
                .orElseThrow(() -> ApiException.badRequest("Transaction introuvable."));
        Map<String, String> values = fields(contact, deal);
        return new Draft(MergeFields.merge(template.getSubject(), values), MergeFields.merge(template.getBody(), values),
                effectiveDeal);
    }

    /**
     * Envoie l'e-mail et l'enregistre. En cas d'échec SMTP, l'envoi reste tracé en ERREUR et une ApiException est
     * levée : l'appelant doit la laisser sortir de sa transaction avec {@code noRollbackFor = ApiException.class}
     * et n'effectuer ses propres changements d'état qu'après cet appel.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public void deliver(Delivery d) {
        Contact contact = contacts.findById(d.contactId())
                .orElseThrow(() -> ApiException.badRequest("Contact introuvable."));
        if (contact.getEmail() == null) {
            throw ApiException.badRequest("Ce contact n'a pas d'adresse e-mail.");
        }
        Instant now = clock.instant();
        UUID trackingId = UUID.randomUUID();
        var record = new SentEmail(contact.getId(), contact.getEmail(), d.dealId(), d.quoteId(), d.subject(), d.body(),
                now, trackingId);
        try {
            sendMime(contact.getEmail(), d, trackingId);
        } catch (Exception e) {
            log.warn("Envoi de l'e-mail à {} impossible : {}", contact.getEmail(), e.getMessage());
            record.markFailed(e.getMessage());
            sent.save(record);
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "L'e-mail n'a pas pu être envoyé. Vérifiez la configuration SMTP ou réessayez.");
        }
        sent.save(record);
        activities.save(new Activity(ActivityType.EMAIL, d.subject() + "\n\n" + d.body(), now, d.authorId(), d.dealId(),
                contact.getId(), contact.getCompanyId()));
    }

    /** Règle 14 : la première ouverture du pixel passe l'envoi en OUVERT. Silencieux si le jeton est inconnu. */
    @Transactional
    public void trackOpen(UUID trackingId) {
        sent.findByTrackingId(trackingId).ifPresent(e -> e.markOpened(clock.instant()));
    }

    private void sendMime(String to, Delivery d, UUID trackingId) throws Exception {
        var message = sender.createMimeMessage();
        var helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
        helper.setFrom(app.mailFrom(), "Red Sea Bunkering");
        if (d.authorId() != null) {
            var author = users.findById(d.authorId()).orElse(null);
            if (author != null) {
                helper.setReplyTo(author.getEmail());
            }
        }
        helper.setTo(to);
        helper.setSubject(d.subject());
        helper.setText(d.body(), html(d.body(), trackingId));
        if (d.attachmentName() != null) {
            helper.addAttachment(d.attachmentName(), new ByteArrayResource(d.attachment()), "application/pdf");
        }
        sender.send(message);
    }

    private String html(String body, UUID trackingId) {
        String pixel = app.publicUrl() + "/api/public/t/" + trackingId + ".png";
        return "<div style=\"font-family:Arial,sans-serif;font-size:14px;line-height:1.5;color:#16191c\">"
                + HtmlUtils.htmlEscape(body).replace("\n", "<br>")
                + "</div><img src=\"" + HtmlUtils.htmlEscape(pixel) + "\" width=\"1\" height=\"1\" alt=\"\">";
    }

    private Map<String, String> fields(Contact contact, Deal deal) {
        Map<String, String> m = new HashMap<>();
        m.put("prenom", contact == null ? "" : contact.getFirstName());
        UUID companyId = deal != null && deal.getCompanyId() != null ? deal.getCompanyId()
                : contact == null ? null : contact.getCompanyId();
        m.put("entreprise", companyId == null ? "" : companies.findById(companyId).map(c -> c.getName()).orElse(""));
        m.put("navire", deal == null ? "votre navire" : deal.getVesselName());
        if (deal != null) {
            List<String> label = jdbc.queryForList("select label from fuel_grades where code = ?", String.class,
                    deal.getFuelGrade());
            m.put("produit", label.isEmpty() ? deal.getFuelGrade() : label.get(0));
            m.put("quantite", FrenchFormat.number(deal.getQuantityMt()) + " MT");
            m.put("eta", FrenchFormat.longDate(deal.getEta()));
        }
        UUID ownerId = deal != null ? deal.getOwnerId() : contact == null ? null : contact.getOwnerId();
        m.put("commercial", ownerId == null ? "" : users.findById(ownerId).map(u -> u.fullName()).orElse(""));
        return m;
    }

    private UUID firstOpenDeal(UUID contactId) {
        return jdbc.query("""
                select d.id from deals d join pipeline_stages s on s.code = d.stage
                where d.contact_id = ? and not s.is_won and not s.is_lost
                order by d.eta nulls last, d.created_at limit 1""",
                (rs, i) -> rs.getObject(1, UUID.class), contactId).stream().findFirst().orElse(null);
    }

    public record Draft(String subject, String body, UUID dealId) {
    }

    public record Delivery(UUID contactId, UUID dealId, UUID quoteId, String subject, String body, UUID authorId,
            String attachmentName, byte[] attachment) {
    }
}
