package dj.redseabunkering.crm.lead;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
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
import dj.redseabunkering.crm.company.Company;
import dj.redseabunkering.crm.company.CompanyRepository;
import dj.redseabunkering.crm.company.CompanyType;
import dj.redseabunkering.crm.contact.Contact;
import dj.redseabunkering.crm.contact.ContactRepository;
import dj.redseabunkering.crm.contact.Lifecycle;
import dj.redseabunkering.crm.deal.Deal;
import dj.redseabunkering.crm.deal.DealRepository;
import dj.redseabunkering.crm.deal.DealService;
import dj.redseabunkering.crm.lead.LeadDtos.ConversionResult;
import dj.redseabunkering.crm.lead.LeadDtos.LeadDto;
import dj.redseabunkering.crm.lead.LeadDtos.LeadRequest;
import dj.redseabunkering.crm.task.Task;
import dj.redseabunkering.crm.task.TaskPriority;
import dj.redseabunkering.crm.task.TaskRepository;

/** Leads entrants : saisie, rejet et conversion en entreprise + contact + transaction + tâche (règle 3). */
@Service
public class LeadService {

    /** Commercial actif (rôle SALES) ayant le moins de transactions ouvertes ; ordre alphabétique en cas d'égalité. */
    private static final String LEAST_LOADED = """
            select u.id, u.first_name || ' ' || u.last_name from users u
            where u.status = 'ACTIVE' and u.role = 'SALES'
            order by (select count(*) from deals d join pipeline_stages s on s.code = d.stage
                      where d.owner_id = u.id and not s.is_won and not s.is_lost),
                     u.last_name, u.first_name
            limit 1""";

    private final LeadRepository leads;
    private final CompanyRepository companies;
    private final ContactRepository contacts;
    private final DealRepository deals;
    private final DealService dealService;
    private final TaskRepository tasks;
    private final ActivityRepository activities;
    private final AuditService audit;
    private final BusinessCalendar calendar;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public LeadService(LeadRepository leads, CompanyRepository companies, ContactRepository contacts,
            DealRepository deals, DealService dealService, TaskRepository tasks, ActivityRepository activities,
            AuditService audit, BusinessCalendar calendar, JdbcTemplate jdbc, Clock clock) {
        this.leads = leads;
        this.companies = companies;
        this.contacts = contacts;
        this.deals = deals;
        this.dealService = dealService;
        this.tasks = tasks;
        this.activities = activities;
        this.audit = audit;
        this.calendar = calendar;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<LeadDto> list(LeadStatus status) {
        var list = status == null ? leads.findAllByOrderByReceivedAtDesc() : leads.findByStatusOrderByReceivedAtDesc(status);
        return list.stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public LeadDto get(UUID id) {
        return toDto(find(id));
    }

    @Transactional
    public UUID create(LeadRequest req) {
        dealService.referencePrice(req.fuelGrade());
        var lead = new Lead(req.name(), clock.instant());
        lead.describe(req.companyName(), req.email(), req.phone(), req.vesselName(), req.imo(), req.fuelGrade(),
                req.quantityMt(), req.eta(), req.deliveryLocation(), req.source(), req.message());
        return leads.save(lead).getId();
    }

    /**
     * Règle 3, en une seule transaction : entreprise retrouvée par nom (casse ignorée) ou créée, contact SQL
     * (réutilisé si l'e-mail existe déjà), transaction « Qualifiée » au prix de référence, tâche de cotation du jour
     * en priorité haute, le tout attribué au commercial le moins chargé ; activité système avec le message.
     */
    @Transactional
    public ConversionResult convert(UUID id, CurrentUser me) {
        Lead lead = find(id);
        if (lead.getStatus() != LeadStatus.NOUVEAU) {
            throw ApiException.conflict("Ce lead a déjà été traité.");
        }
        if (lead.getVesselName() == null || lead.getFuelGrade() == null || lead.getQuantityMt() == null) {
            throw ApiException.badRequest("Navire, produit et quantité sont nécessaires pour créer la transaction.");
        }
        var owner = jdbc.query(LEAST_LOADED, (rs, i) -> Map.entry(rs.getObject(1, UUID.class), rs.getString(2)))
                .stream().findFirst()
                .orElseGet(() -> Map.entry(me.id(), "vous (aucun commercial actif)"));
        UUID ownerId = owner.getKey();

        boolean companyCreated = false;
        Company company = null;
        if (lead.getCompanyName() != null) {
            company = companies.findByNameIgnoreCase(lead.getCompanyName()).orElse(null);
            if (company == null) {
                company = new Company(lead.getCompanyName(), CompanyType.ARMATEUR, ownerId);
                company.update(lead.getCompanyName(), CompanyType.ARMATEUR, null, null, null, emailDomain(lead.getEmail()));
                companies.save(company);
                companyCreated = true;
            }
        }
        UUID companyId = company == null ? null : company.getId();

        boolean contactCreated = false;
        Contact contact = lead.getEmail() == null ? null
                : contacts.findByEmailIgnoreCase(lead.getEmail()).stream().findFirst().orElse(null);
        if (contact == null) {
            String[] parts = lead.getName().trim().split("\\s+", 2);
            contact = new Contact(parts[0], parts.length > 1 ? parts[1] : "—", ownerId);
            contact.update(parts[0], parts.length > 1 ? parts[1] : "—", "Acheteur", lead.getEmail(), lead.getPhone(),
                    companyId, Lifecycle.SQL, lead.getSource());
            contacts.save(contact);
            contactCreated = true;
        } else {
            contact.setLifecycle(contact.getLifecycle().atLeast(Lifecycle.SQL));
        }

        BigDecimal price = dealService.referencePrice(lead.getFuelGrade());
        var deal = new Deal(lead.getVesselName(), lead.getFuelGrade(), lead.getQuantityMt(), price, "QUALIFIEE", ownerId);
        deal.describe(lead.getVesselName(), lead.getImo(), companyId, contact.getId(), lead.getFuelGrade(),
                lead.getQuantityMt(), price, lead.getSource(), lead.getEta(), lead.getDeliveryLocation());
        deals.save(deal);

        tasks.save(new Task("Envoyer la cotation à " + lead.getName(), calendar.today(), TaskPriority.HAUTE, ownerId,
                deal.getId(), contact.getId(), companyId));
        String text = "Lead converti depuis " + (lead.getSource() == null ? "une source inconnue" : lead.getSource())
                + (lead.getMessage() == null ? "." : ". Message : « " + lead.getMessage() + " »");
        activities.save(new Activity(ActivityType.SYSTEM, text, clock.instant(), me.id(), deal.getId(),
                contact.getId(), companyId));

        lead.markConverted(deal.getId());
        audit.record(me.id(), "LEAD_CONVERTED", "lead", lead.getId(),
                Map.of("deal", deal.getId().toString(), "owner", ownerId.toString()));
        return new ConversionResult(deal.getId(), contact.getId(), companyId, ownerId, owner.getValue(),
                companyCreated, contactCreated);
    }

    @Transactional
    public void reject(UUID id, CurrentUser me) {
        Lead lead = find(id);
        if (lead.getStatus() != LeadStatus.NOUVEAU) {
            throw ApiException.conflict("Ce lead a déjà été traité.");
        }
        lead.markRejected();
        audit.record(me.id(), "LEAD_REJECTED", "lead", id, Map.of());
    }

    private Lead find(UUID id) {
        return leads.findById(id).orElseThrow(() -> ApiException.notFound("Lead introuvable."));
    }

    private LeadDto toDto(Lead l) {
        BigDecimal estimate = l.getQuantityMt() == null || l.getFuelGrade() == null ? null
                : l.getQuantityMt().multiply(dealService.referencePrice(l.getFuelGrade()));
        return new LeadDto(l.getId(), l.getName(), l.getCompanyName(), l.getEmail(), l.getPhone(), l.getVesselName(),
                l.getImo(), l.getFuelGrade(), l.getQuantityMt(), l.getEta(), l.getDeliveryLocation(), l.getSource(),
                l.getMessage(), l.getStatus(), l.getReceivedAt(), l.getConvertedDealId(), estimate);
    }

    private static String emailDomain(String email) {
        if (email == null || !email.contains("@")) {
            return null;
        }
        return email.substring(email.indexOf('@') + 1);
    }
}
