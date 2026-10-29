package dj.redseabunkering.crm.deal;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dj.redseabunkering.crm.activity.Activity;
import dj.redseabunkering.crm.activity.ActivityRepository;
import dj.redseabunkering.crm.activity.ActivityType;
import dj.redseabunkering.crm.audit.AuditService;
import dj.redseabunkering.crm.common.ApiException;
import dj.redseabunkering.crm.common.BusinessCalendar;
import dj.redseabunkering.crm.common.CurrentUser;
import dj.redseabunkering.crm.company.CompanyRepository;
import dj.redseabunkering.crm.contact.Contact;
import dj.redseabunkering.crm.contact.ContactRepository;
import dj.redseabunkering.crm.contact.Lifecycle;
import dj.redseabunkering.crm.deal.DealDtos.DealDetail;
import dj.redseabunkering.crm.deal.DealDtos.DealRequest;
import dj.redseabunkering.crm.user.OwnerAssignment;

/**
 * Transactions et règles du pipeline :
 * <ul>
 * <li>4 — passage en livrée : date de clôture, contact Client, montant compté dans le CA ;</li>
 * <li>5 — passage en perdue : motif obligatoire ;</li>
 * <li>6 — retour à une étape ouverte : clôture et motif effacés, contact au moins Opportunité ;</li>
 * <li>7 — chaque changement d'étape : activité système « Étape : X → Y » et ligne d'audit.</li>
 * </ul>
 */
@Service
public class DealService {

    private final DealRepository deals;
    private final PipelineStageRepository stages;
    private final ContactRepository contacts;
    private final CompanyRepository companies;
    private final ActivityRepository activities;
    private final AuditService audit;
    private final OwnerAssignment owners;
    private final DealQueries queries;
    private final RelatedRecords related;
    private final BusinessCalendar calendar;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public DealService(DealRepository deals, PipelineStageRepository stages, ContactRepository contacts,
            CompanyRepository companies, ActivityRepository activities, AuditService audit, OwnerAssignment owners,
            DealQueries queries, RelatedRecords related, BusinessCalendar calendar, JdbcTemplate jdbc, Clock clock) {
        this.deals = deals;
        this.stages = stages;
        this.contacts = contacts;
        this.companies = companies;
        this.activities = activities;
        this.audit = audit;
        this.owners = owners;
        this.queries = queries;
        this.related = related;
        this.calendar = calendar;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DealDetail detail(UUID id, CurrentUser me) {
        var row = queries.find(id).orElseThrow(DealService::notFound);
        return new DealDetail(row, related.tasksOfDeal(id), me.canEdit(row.ownerId()));
    }

    @Transactional
    public UUID create(DealRequest req, CurrentUser me) {
        UUID companyId = companyOf(req);
        validateReferences(req, companyId);
        PipelineStage initial = req.stage() == null || req.stage().isBlank() ? stage("RECUE") : stage(req.stage());
        if (!initial.isOpen()) {
            throw ApiException.badRequest("Une nouvelle transaction commence à une étape ouverte.");
        }
        BigDecimal price = req.priceUsdPerMt() != null ? req.priceUsdPerMt() : referencePrice(req.fuelGrade());
        var deal = new Deal(req.vesselName(), req.fuelGrade(), req.quantityMt(), price, initial.getCode(),
                owners.forNewRecord(req.ownerId(), me));
        deal.describe(req.vesselName(), req.imo(), companyId, req.contactId(), req.fuelGrade(), req.quantityMt(), price,
                req.source(), req.eta(), req.deliveryLocation());
        deals.save(deal);
        system(deal, "Transaction créée : " + deal.title(), me.id());
        audit.record(me.id(), "DEAL_CREATED", "deal", deal.getId(), Map.of("stage", initial.getCode()));
        return deal.getId();
    }

    @Transactional
    public void update(UUID id, DealRequest req, CurrentUser me) {
        Deal deal = get(id);
        me.requireEdit(deal.getOwnerId());
        UUID companyId = companyOf(req);
        validateReferences(req, companyId);
        BigDecimal price = req.priceUsdPerMt() != null ? req.priceUsdPerMt() : deal.getPriceUsdPerMt();
        deal.describe(req.vesselName(), req.imo(), companyId, req.contactId(), req.fuelGrade(), req.quantityMt(), price,
                req.source(), req.eta(), req.deliveryLocation());
        deal.setOwnerId(owners.reassign(deal.getOwnerId(), req.ownerId(), me));
        if (req.stage() != null && !req.stage().isBlank() && !req.stage().equals(deal.getStage())) {
            applyStage(deal, stage(req.stage()), req.lostReason(), me);
        }
    }

    @Transactional
    public void changeStage(UUID id, String stageCode, String lostReason, CurrentUser me) {
        Deal deal = get(id);
        me.requireEdit(deal.getOwnerId());
        if (stageCode.equals(deal.getStage())) {
            return;
        }
        applyStage(deal, stage(stageCode), lostReason, me);
    }

    /** Point d'entrée commun des changements d'étape (pipeline, fiche, devis accepté…). */
    @Transactional
    public void applyStage(Deal deal, PipelineStage target, String lostReason, CurrentUser me) {
        if (target.isLost() && (lostReason == null || lostReason.isBlank())) {
            throw ApiException.badRequest("Indiquez le motif de perte.");
        }
        PipelineStage previous = stage(deal.getStage());
        deal.moveTo(target, target.isLost() ? lostReason.trim() : null, calendar.today());

        if (deal.getContactId() != null) {
            contacts.findById(deal.getContactId()).ifPresent(c -> updateLifecycle(c, target));
        }
        String text = "Étape : " + previous.getLabel() + " → " + target.getLabel()
                + (target.isLost() ? " (motif : " + deal.getLostReason() + ")" : "");
        system(deal, text, me.id());

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("from", previous.getCode());
        details.put("to", target.getCode());
        if (target.isLost()) {
            details.put("lostReason", deal.getLostReason());
        }
        audit.record(me.id(), "DEAL_STAGE_CHANGED", "deal", deal.getId(), details);
    }

    @Transactional
    public void changeOwner(UUID id, UUID ownerId, CurrentUser me) {
        Deal deal = get(id);
        UUID before = deal.getOwnerId();
        deal.setOwnerId(owners.reassign(before, ownerId, me));
        if (!deal.getOwnerId().equals(before)) {
            audit.record(me.id(), "DEAL_REASSIGNED", "deal", id, Map.of("to", deal.getOwnerId().toString()));
        }
    }

    @Transactional
    public void delete(UUID id, CurrentUser me) {
        Deal deal = get(id);
        me.requireEdit(deal.getOwnerId());
        audit.record(me.id(), "DEAL_DELETED", "deal", id, Map.of("title", deal.title()));
        deals.delete(deal);
    }

    Deal get(UUID id) {
        return deals.findById(id).orElseThrow(DealService::notFound);
    }

    PipelineStage stage(String code) {
        return stages.findByCode(code).orElseThrow(() -> ApiException.badRequest("Étape inconnue : " + code));
    }

    public BigDecimal referencePrice(String fuelGrade) {
        return jdbc.query("select reference_price from fuel_grades where code = ?",
                (rs, i) -> rs.getBigDecimal(1), fuelGrade).stream().findFirst()
                .orElseThrow(() -> ApiException.badRequest("Produit inconnu : " + fuelGrade));
    }

    /** Règles 4 et 6 côté contact. */
    private static void updateLifecycle(Contact contact, PipelineStage target) {
        if (target.isWon()) {
            contact.setLifecycle(Lifecycle.CLIENT);
        } else if (target.isOpen()) {
            contact.setLifecycle(contact.getLifecycle().atLeast(Lifecycle.OPPORTUNITE));
        }
    }

    private void system(Deal deal, String text, UUID authorId) {
        activities.save(new Activity(ActivityType.SYSTEM, text, clock.instant(), authorId, deal.getId(),
                deal.getContactId(), deal.getCompanyId()));
    }

    /** Sans entreprise saisie, on reprend celle du contact. */
    private UUID companyOf(DealRequest req) {
        if (req.companyId() != null || req.contactId() == null) {
            return req.companyId();
        }
        return contacts.findById(req.contactId()).map(Contact::getCompanyId).orElse(null);
    }

    private void validateReferences(DealRequest req, UUID companyId) {
        if (companyId != null && !companies.existsById(companyId)) {
            throw ApiException.badRequest("L'entreprise choisie n'existe pas.");
        }
        if (req.contactId() != null && !contacts.existsById(req.contactId())) {
            throw ApiException.badRequest("Le contact choisi n'existe pas.");
        }
        referencePrice(req.fuelGrade());
        if (req.deliveryLocation() != null && !req.deliveryLocation().isBlank()) {
            Integer n = jdbc.queryForObject("select count(*) from delivery_locations where code = ?", Integer.class,
                    req.deliveryLocation());
            if (n == null || n == 0) {
                throw ApiException.badRequest("Lieu de livraison inconnu.");
            }
        }
    }

    private static ApiException notFound() {
        return ApiException.notFound("Transaction introuvable.");
    }
}
