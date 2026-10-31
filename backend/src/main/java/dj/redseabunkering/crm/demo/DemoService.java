package dj.redseabunkering.crm.demo;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dj.redseabunkering.crm.audit.AuditService;
import dj.redseabunkering.crm.common.ApiException;
import dj.redseabunkering.crm.common.AppProperties;
import dj.redseabunkering.crm.common.BusinessCalendar;
import dj.redseabunkering.crm.common.CurrentUser;
import dj.redseabunkering.crm.common.SecureTokens;
import dj.redseabunkering.crm.demo.DemoData.ActivityRow;
import dj.redseabunkering.crm.demo.DemoData.CampaignRow;
import dj.redseabunkering.crm.demo.DemoData.CompanyRow;
import dj.redseabunkering.crm.demo.DemoData.ContactRow;
import dj.redseabunkering.crm.demo.DemoData.DealRow;
import dj.redseabunkering.crm.demo.DemoData.LeadRow;
import dj.redseabunkering.crm.demo.DemoData.OwnerRow;
import dj.redseabunkering.crm.demo.DemoData.QuoteRow;
import dj.redseabunkering.crm.demo.DemoData.SentRow;
import dj.redseabunkering.crm.demo.DemoData.TaskRow;
import dj.redseabunkering.crm.user.Role;
import dj.redseabunkering.crm.user.User;
import dj.redseabunkering.crm.user.UserRepository;
import dj.redseabunkering.crm.user.UserStatus;

/**
 * Données de démonstration reprises du prototype. Deux garde-fous protègent de vraies données : le chargement n'a lieu
 * que sur une base métier vide, et la réinitialisation exige que cette base ait bien été remplie par ce service.
 */
@Service
public class DemoService {

    private static final Logger log = LoggerFactory.getLogger(DemoService.class);
    private static final String MARKER = "DEMO_SEEDED";
    private static final String LAST_QUOTE_NUMBER_SQL = """
            insert into quote_number_sequences (year, last_value) values (?, ?)
            on conflict (year) do update set last_value = excluded.last_value""";

    private final JdbcTemplate jdbc;
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final AuditService audit;
    private final AppProperties app;
    private final DemoProperties props;
    private final BusinessCalendar calendar;
    private final Clock clock;

    public DemoService(JdbcTemplate jdbc, UserRepository users, PasswordEncoder passwords, AuditService audit,
            AppProperties app, DemoProperties props, BusinessCalendar calendar, Clock clock) {
        this.jdbc = jdbc;
        this.users = users;
        this.passwords = passwords;
        this.audit = audit;
        this.app = app;
        this.props = props;
        this.calendar = calendar;
        this.clock = clock;
    }

    @Transactional
    public boolean seedIfEmpty() {
        Long existing = jdbc.queryForObject("""
                select (select count(*) from companies) + (select count(*) from contacts)
                     + (select count(*) from deals) + (select count(*) from leads)""", Long.class);
        if (existing != null && existing > 0) {
            log.info("Base déjà remplie : données de démonstration non chargées.");
            return false;
        }
        load(null);
        return true;
    }

    /** Efface toutes les données métier et recharge la démonstration ; comptes, référentiels et modèles sont gardés. */
    @Transactional
    public void reset(CurrentUser me) {
        if (!app.seedDemo()) {
            throw ApiException.conflict("Les données de démonstration ne sont pas activées sur ce serveur.");
        }
        Long seeded = jdbc.queryForObject("select count(*) from audit_log where action = ?", Long.class, MARKER);
        if (seeded == null || seeded == 0) {
            throw ApiException.conflict("Cette base n'a pas été remplie par la démonstration : réinitialisation refusée.");
        }
        for (String table : new String[] { "sent_emails", "quotes", "sequence_enrollments", "tasks", "activities",
                "leads", "deals", "contacts", "companies", "campaigns" }) {
            jdbc.update("delete from " + table);
        }
        jdbc.update("update sequences set auto_send = false");
        load(me.id());
    }

    private void load(UUID actor) {
        LocalDate ref = DemoData.reference(props.referenceDate(), calendar.today());
        Instant now = clock.instant();
        Instant noon = ref.atTime(LocalTime.NOON).atZone(BusinessCalendar.ZONE).toInstant();
        Instant anchor = noon.isAfter(now) ? now : noon;

        Map<String, UUID> owners = owners();
        Map<String, UUID> companies = new HashMap<>();
        Map<String, UUID> contacts = new HashMap<>();
        Map<String, String> contactEmails = new HashMap<>();
        Map<String, UUID> deals = new HashMap<>();

        int i = 0;
        for (CompanyRow c : DemoData.COMPANIES) {
            UUID id = UUID.randomUUID();
            companies.put(c.key(), id);
            jdbc.update("""
                    insert into companies (id, name, type, country, city, fleet_size, website, owner_id, created_at)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                    id, c.name(), c.type(), c.country(), c.city(), c.fleet(), c.web(),
                    owners.get("u" + (i % 3 + 1)), day(ref, -260 + i * 9));
            i++;
        }
        i = 0;
        for (ContactRow c : DemoData.CONTACTS) {
            UUID id = UUID.randomUUID();
            CompanyRow company = DemoData.company(c.company());
            String email = DemoData.email(c.first(), c.last(), company.web());
            contacts.put(c.key(), id);
            contactEmails.put(c.key(), email);
            jdbc.update("""
                    insert into contacts (id, first_name, last_name, title, email, phone, company_id, lifecycle, source,
                                          owner_id, created_at)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                    id, c.first(), c.last(), c.title(), email, DemoData.phone(company.country(), i),
                    companies.get(c.company()), c.lifecycle(), c.source(), owners.get(c.owner()),
                    day(ref, -200 + i * 7));
            i++;
        }
        i = 0;
        for (DealRow d : DemoData.DEALS) {
            UUID id = UUID.randomUUID();
            deals.put("d" + (i + 1), id);
            boolean closed = d.stage().equals("LIVREE") || d.stage().equals("PERDUE");
            jdbc.update("""
                    insert into deals (id, vessel_name, imo, company_id, contact_id, fuel_grade, quantity_mt,
                                       price_usd_per_mt, stage, owner_id, source, eta, delivery_location, closed_at,
                                       lost_reason, created_at)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                    id, d.vessel(), d.imo(), companies.get(d.company()), contacts.get(d.contact()), d.grade(),
                    BigDecimal.valueOf(d.qty()), DemoData.price(d.grade(), i), d.stage(), owners.get(d.owner()),
                    d.source(), ref.plusDays(d.eta()), i % 3 == 0 ? "ANCHORAGE" : "DMP",
                    closed ? ref.plusDays(d.eta()) : null, DemoData.lostReason(i, d.stage()), day(ref, d.created()));
            i++;
        }
        for (LeadRow l : DemoData.LEADS) {
            Timestamp received = Timestamp.from(anchor.minus(l.minutesAgo(), ChronoUnit.MINUTES));
            jdbc.update("""
                    insert into leads (id, name, company_name, email, phone, vessel_name, imo, fuel_grade, quantity_mt, eta,
                                       delivery_location, source, message, status, received_at)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                    UUID.randomUUID(), l.name(), l.company(), l.email(), l.phone(), l.vessel(), l.imo(), l.grade(),
                    BigDecimal.valueOf(l.qty()), ref.plusDays(l.eta()), l.location(), l.source(), l.message(), l.status(),
                    received);
        }
        for (ActivityRow a : DemoData.ACTIVITIES) {
            String contact = a.deal() != null ? DemoData.deal(a.deal()).contact() : a.contact();
            String company = a.deal() != null ? DemoData.deal(a.deal()).company() : a.company();
            jdbc.update("""
                    insert into activities (id, type, text, occurred_at, author_id, deal_id, contact_id, company_id)
                    values (?, ?, ?, ?, ?, ?, ?, ?)""",
                    UUID.randomUUID(), a.type(), a.text(), day(ref, a.day()), owners.get(a.by()),
                    a.deal() == null ? null : deals.get(a.deal()), contacts.get(contact), companies.get(company));
        }
        for (TaskRow t : DemoData.TASKS) {
            String contact = t.deal() != null ? DemoData.deal(t.deal()).contact() : t.contact();
            String company = t.deal() != null ? DemoData.deal(t.deal()).company() : t.company();
            jdbc.update("""
                    insert into tasks (id, title, due_date, priority, owner_id, done, done_at, deal_id, contact_id, company_id)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                    UUID.randomUUID(), t.title(), ref.plusDays(t.due()), t.priority(), owners.get(t.owner()), t.done(),
                    t.done() ? day(ref, t.due()) : null, t.deal() == null ? null : deals.get(t.deal()),
                    contacts.get(contact), companies.get(company));
        }
        Map<String, UUID> quotes = new HashMap<>();
        for (QuoteRow q : DemoData.QUOTES) {
            UUID id = UUID.randomUUID();
            quotes.put(q.deal(), id);
            Timestamp created = day(ref, q.created());
            // Comme dans le prototype, les devis envoyés sont encore valides le jour J
            Timestamp validUntil = q.status().equals("ENVOYE") ? Timestamp.from(noon.plus(48, ChronoUnit.HOURS))
                    : Timestamp.from(created.toInstant().plus(48, ChronoUnit.HOURS));
            jdbc.update("""
                    insert into quotes (id, number, deal_id, price_usd_per_mt, barge_fee_usd, status, valid_until, sent_at,
                                        created_at)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                    id, "RSB-Q-" + ref.getYear() + "-" + q.number(), deals.get(q.deal()), BigDecimal.valueOf(q.price()),
                    BigDecimal.valueOf(q.barge()), q.status(), validUntil, created, created);
        }
        jdbc.update(LAST_QUOTE_NUMBER_SQL, ref.getYear(), DemoData.LAST_QUOTE_NUMBER);
        for (SentRow s : DemoData.SENT) {
            Timestamp at = day(ref, s.day());
            jdbc.update("""
                    insert into sent_emails (id, to_contact_id, to_address, deal_id, quote_id, subject, body, status, sent_at,
                                             opened_at, tracking_id)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                    UUID.randomUUID(), contacts.get(s.contact()), contactEmails.get(s.contact()), deals.get(s.deal()),
                    s.subject().startsWith("Cotation") ? quotes.get(s.deal()) : null, s.subject(),
                    "Message de démonstration.", s.status(), at, s.status().equals("OUVERT") ? at : null, UUID.randomUUID());
        }
        for (CampaignRow c : DemoData.CAMPAIGNS) {
            jdbc.update("""
                    insert into campaigns (id, name, channel, spend_usd, impressions, clicks, period_start, period_end)
                    values (?, ?, ?, ?, ?, ?, ?, ?)""",
                    UUID.randomUUID(), c.name(), c.channel(), BigDecimal.valueOf(c.spend()), c.impressions(), c.clicks(),
                    ref.minusMonths(6), ref);
        }
        jdbc.update("""
                insert into sequence_enrollments (id, contact_id, sequence_id, enrolled_at)
                select ?, ?, id, ? from sequences order by created_at limit 1""",
                UUID.randomUUID(), contacts.get(DemoData.ENROLLED_CONTACT), day(ref, -1));

        audit.record(actor, MARKER, "demo", UUID.randomUUID(), Map.of("reference", ref.toString()));
        log.info("Données de démonstration chargées (jour J : {}).", ref);
    }

    /** Commerciaux de démonstration : un compte existant garde son mot de passe. */
    private Map<String, UUID> owners() {
        Map<String, UUID> ids = new HashMap<>();
        String password = props.password();
        if (password == null || password.isBlank()) {
            log.warn("APP_DEMO_PASSWORD absent : les commerciaux de démonstration ne pourront pas se connecter.");
            password = SecureTokens.generate();
        }
        String hash = null;
        for (OwnerRow o : DemoData.OWNERS) {
            User user = users.findByEmail(o.email()).orElse(null);
            if (user == null) {
                if (hash == null) {
                    hash = passwords.encode(password);
                }
                user = new User(o.email(), hash, o.first(), o.last());
                user.setRole(Role.SALES);
                user.markEmailVerified(clock.instant(), false);
                user.setStatus(UserStatus.ACTIVE);
                user = users.saveAndFlush(user);
            }
            ids.put(o.key(), user.getId());
        }
        return ids;
    }

    private static Timestamp day(LocalDate ref, int offset) {
        return Timestamp.from(ref.plusDays(offset).atTime(9, 0).atZone(BusinessCalendar.ZONE).toInstant());
    }
}
