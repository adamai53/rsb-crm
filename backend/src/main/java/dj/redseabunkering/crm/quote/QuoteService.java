package dj.redseabunkering.crm.quote;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dj.redseabunkering.crm.audit.AuditService;
import dj.redseabunkering.crm.common.ApiException;
import dj.redseabunkering.crm.common.BusinessCalendar;
import dj.redseabunkering.crm.common.CurrentUser;
import dj.redseabunkering.crm.deal.Deal;
import dj.redseabunkering.crm.deal.DealService;
import dj.redseabunkering.crm.deal.PipelineStage;
import dj.redseabunkering.crm.email.EmailService;
import dj.redseabunkering.crm.email.EmailService.Delivery;
import dj.redseabunkering.crm.email.EmailService.Draft;
import dj.redseabunkering.crm.quote.QuoteDtos.QuoteEmail;
import dj.redseabunkering.crm.quote.QuoteDtos.QuoteRequest;
import dj.redseabunkering.crm.quote.QuoteDtos.QuoteRow;

/**
 * Devis (règles 8 à 10) : numéro annuel, validité 48 h, barge 1 450 USD par défaut ; envoi réel avec le PDF, qui amène
 * la transaction au moins à « Cotation » ; acceptation qui reporte le prix et passe la transaction en « Nomination ».
 */
@Service
public class QuoteService {

    static final Duration VALIDITY = Duration.ofHours(48);
    static final BigDecimal DEFAULT_BARGE_FEE = BigDecimal.valueOf(1450);
    private static final String SEND_TEMPLATE = "Envoi de cotation";

    private final QuoteRepository quotes;
    private final QuoteQueries queries;
    private final QuotePdfRenderer pdf;
    private final DealService deals;
    private final EmailService emails;
    private final AuditService audit;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public QuoteService(QuoteRepository quotes, QuoteQueries queries, QuotePdfRenderer pdf, DealService deals,
            EmailService emails, AuditService audit, JdbcTemplate jdbc, Clock clock) {
        this.quotes = quotes;
        this.queries = queries;
        this.pdf = pdf;
        this.deals = deals;
        this.emails = emails;
        this.audit = audit;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional
    public UUID create(QuoteRequest req, CurrentUser me) {
        Deal deal = deals.get(req.dealId());
        me.requireEdit(deal.getOwnerId());
        requireOpen(deal);
        BigDecimal price = req.priceUsdPerMt() != null ? req.priceUsdPerMt() : deal.getPriceUsdPerMt();
        BigDecimal barge = req.bargeFeeUsd() != null ? req.bargeFeeUsd() : DEFAULT_BARGE_FEE;
        Instant now = clock.instant();
        var quote = new Quote(nextNumber(now), deal.getId(), price, barge, now.plus(VALIDITY));
        return quotes.save(quote).getId();
    }

    @Transactional(readOnly = true)
    public QuoteRow row(UUID id) {
        return queries.find(id).orElseThrow(QuoteService::notFound);
    }

    @Transactional(readOnly = true)
    public byte[] pdf(UUID id) {
        return pdf.render(row(id));
    }

    @Transactional(readOnly = true)
    public Draft emailDraft(UUID id) {
        QuoteRow q = row(id);
        return emails.draftFromTemplateNamed(SEND_TEMPLATE, q.contactId(), q.dealId());
    }

    /** L'e-mail part d'abord : si le SMTP échoue, ni le devis ni la transaction ne changent. */
    @Transactional(noRollbackFor = ApiException.class)
    public void send(UUID id, QuoteEmail email, CurrentUser me) {
        Quote quote = get(id);
        Deal deal = deals.get(quote.getDealId());
        me.requireEdit(deal.getOwnerId());
        requireOpen(deal);
        if (quote.getStatus() != QuoteStatus.BROUILLON && quote.getStatus() != QuoteStatus.ENVOYE) {
            throw ApiException.conflict("Ce devis ne peut plus être envoyé.");
        }
        Instant now = clock.instant();
        if (quote.getValidUntil().isBefore(now)) {
            throw ApiException.conflict("Ce devis a expiré. Créez-en un nouveau.");
        }
        if (deal.getContactId() == null) {
            throw ApiException.badRequest("Associez d'abord un contact à la transaction.");
        }
        QuoteRow row = row(id);
        emails.deliver(new Delivery(deal.getContactId(), deal.getId(), quote.getId(), email.subject().trim(),
                email.body(), me.id(), "Devis-" + quote.getNumber() + ".pdf", pdf.render(row)));

        quote.markSent(now);
        PipelineStage current = deals.stage(deal.getStage());
        PipelineStage cotation = deals.stage("COTATION");
        if (current.getSortOrder() < cotation.getSortOrder()) {
            deals.applyStage(deal, cotation, null, me);
        }
        audit.record(me.id(), "QUOTE_SENT", "quote", quote.getId(), Map.of("number", quote.getNumber()));
    }

    @Transactional
    public void accept(UUID id, CurrentUser me) {
        Quote quote = get(id);
        Deal deal = deals.get(quote.getDealId());
        me.requireEdit(deal.getOwnerId());
        requireOpen(deal);
        if (quote.getStatus() != QuoteStatus.BROUILLON && quote.getStatus() != QuoteStatus.ENVOYE) {
            throw ApiException.conflict("Ce devis ne peut plus être accepté.");
        }
        if (quote.getValidUntil().isBefore(clock.instant())) {
            throw ApiException.conflict("Ce devis a expiré. Créez-en un nouveau.");
        }
        quote.setStatus(QuoteStatus.ACCEPTE);
        deal.setPrice(quote.getPriceUsdPerMt());
        if (!"NOMINATION".equals(deal.getStage())) {
            deals.applyStage(deal, deals.stage("NOMINATION"), null, me);
        }
        audit.record(me.id(), "QUOTE_ACCEPTED", "quote", quote.getId(), Map.of("number", quote.getNumber()));
    }

    @Transactional
    public void refuse(UUID id, CurrentUser me) {
        Quote quote = get(id);
        me.requireEdit(deals.get(quote.getDealId()).getOwnerId());
        if (quote.getStatus() != QuoteStatus.BROUILLON && quote.getStatus() != QuoteStatus.ENVOYE) {
            throw ApiException.conflict("Ce devis est déjà clôturé.");
        }
        quote.setStatus(QuoteStatus.REFUSE);
        audit.record(me.id(), "QUOTE_REFUSED", "quote", quote.getId(), Map.of("number", quote.getNumber()));
    }

    /** Toutes les heures : les devis envoyés au-delà de leur validité passent en « Expiré ». */
    @Scheduled(cron = "0 5 * * * *")
    @Transactional
    public void expireOverdue() {
        quotes.expireSent(clock.instant());
    }

    /** Numéro RSB-Q-AAAA-NNNN : le compteur annuel est incrémenté atomiquement par PostgreSQL. */
    private String nextNumber(Instant now) {
        int year = now.atZone(BusinessCalendar.ZONE).getYear();
        Integer value = jdbc.queryForObject("""
                insert into quote_number_sequences (year, last_value) values (?, 1)
                on conflict (year) do update set last_value = quote_number_sequences.last_value + 1
                returning last_value""", Integer.class, year);
        return "RSB-Q-%d-%04d".formatted(year, value);
    }

    private void requireOpen(Deal deal) {
        if (!deals.stage(deal.getStage()).isOpen()) {
            throw ApiException.conflict("Cette transaction est clôturée.");
        }
    }

    private Quote get(UUID id) {
        return quotes.findById(id).orElseThrow(QuoteService::notFound);
    }

    private static ApiException notFound() {
        return ApiException.notFound("Devis introuvable.");
    }
}
