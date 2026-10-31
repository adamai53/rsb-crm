package dj.redseabunkering.crm.campaign;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dj.redseabunkering.crm.audit.AuditService;
import dj.redseabunkering.crm.campaign.MarketingDtos.CampaignRequest;
import dj.redseabunkering.crm.campaign.MarketingDtos.CampaignRow;
import dj.redseabunkering.crm.campaign.MarketingDtos.ChannelRow;
import dj.redseabunkering.crm.campaign.MarketingDtos.Summary;
import dj.redseabunkering.crm.campaign.MarketingDtos.Totals;
import dj.redseabunkering.crm.common.ApiException;
import dj.redseabunkering.crm.common.CurrentUser;
import dj.redseabunkering.crm.contact.Sources;

/**
 * Campagnes et attribution : une demande revient à la campagne dont le canal est sa source et dont la période
 * contient sa date d'arrivée. Deux campagnes d'un même canal ne peuvent pas se chevaucher, sinon les mêmes leads
 * seraient comptés deux fois.
 */
@Service
public class MarketingService {

    private static final String IN_PERIOD = """
            (c.period_start is null or (%1$s at time zone 'Africa/Djibouti')::date >= c.period_start)
            and (c.period_end is null or (%1$s at time zone 'Africa/Djibouti')::date <= c.period_end)""";

    /** Leads = demandes non rejetées + transactions saisies directement (sans lead d'origine). */
    private static final String SELECT = """
            select c.id, c.name, c.channel, c.spend_usd, c.impressions, c.clicks, c.period_start, c.period_end,
                   (select count(*) from leads l where l.source = c.channel and l.status <> 'REJETE' and %1$s)
                 + (select count(*) from deals d where d.source = c.channel and %2$s
                    and not exists (select 1 from leads o where o.converted_deal_id = d.id)) as leads,
                   w.won, w.revenue
            from campaigns c
            cross join lateral (
                select count(*) as won, coalesce(sum(d.amount_usd), 0) as revenue
                from deals d join pipeline_stages s on s.code = d.stage
                where s.is_won and d.source = c.channel and %2$s
            ) w
            order by c.period_start desc nulls last, c.name
            """.formatted(IN_PERIOD.formatted("l.received_at"), IN_PERIOD.formatted("d.created_at"));

    private final CampaignRepository campaigns;
    private final NamedParameterJdbcTemplate jdbc;
    private final AuditService audit;

    public MarketingService(CampaignRepository campaigns, NamedParameterJdbcTemplate jdbc, AuditService audit) {
        this.campaigns = campaigns;
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<CampaignRow> list() {
        return jdbc.query(SELECT, MarketingService::row);
    }

    @Transactional(readOnly = true)
    public Summary summary() {
        List<CampaignRow> rows = list();
        BigDecimal spend = sum(rows.stream().map(CampaignRow::spendUsd).toList());
        BigDecimal revenue = sum(rows.stream().map(CampaignRow::revenueUsd).toList());
        long leads = rows.stream().mapToLong(CampaignRow::leads).sum();
        long won = rows.stream().mapToLong(CampaignRow::won).sum();
        var totals = new Totals(spend, leads, won, revenue, costPerLead(spend, leads), revenuePerUsd(revenue, spend),
                leads == 0 ? 0 : (int) Math.round(won * 100.0 / leads));
        Map<String, List<CampaignRow>> byChannel = rows.stream().collect(Collectors.groupingBy(CampaignRow::channel));
        List<ChannelRow> channels = byChannel.entrySet().stream().map(e -> {
            BigDecimal s = sum(e.getValue().stream().map(CampaignRow::spendUsd).toList());
            BigDecimal r = sum(e.getValue().stream().map(CampaignRow::revenueUsd).toList());
            long l = e.getValue().stream().mapToLong(CampaignRow::leads).sum();
            long w = e.getValue().stream().mapToLong(CampaignRow::won).sum();
            return new ChannelRow(e.getKey(), s, l, w, r, costPerLead(s, l), revenuePerUsd(r, s));
        }).sorted((a, b) -> a.channel().compareTo(b.channel())).toList();
        return new Summary(totals, rows, channels);
    }

    @Transactional
    public UUID create(CampaignRequest req, CurrentUser me) {
        validate(req, null);
        Campaign c = campaigns.save(new Campaign(req.name(), req.channel(), req.spendUsd(), req.impressions(),
                req.clicks(), req.periodStart(), req.periodEnd()));
        audit.record(me.id(), "CAMPAIGN_CREATED", "campaign", c.getId(), Map.of("channel", req.channel()));
        return c.getId();
    }

    @Transactional
    public void update(UUID id, CampaignRequest req, CurrentUser me) {
        Campaign c = campaigns.findById(id).orElseThrow(() -> ApiException.notFound("Campagne introuvable."));
        validate(req, id);
        c.update(req.name(), req.channel(), req.spendUsd(), req.impressions(), req.clicks(), req.periodStart(),
                req.periodEnd());
        audit.record(me.id(), "CAMPAIGN_UPDATED", "campaign", id, Map.of("channel", req.channel()));
    }

    @Transactional
    public void delete(UUID id, CurrentUser me) {
        if (!campaigns.existsById(id)) {
            throw ApiException.notFound("Campagne introuvable.");
        }
        campaigns.deleteById(id);
        audit.record(me.id(), "CAMPAIGN_DELETED", "campaign", id, Map.of());
    }

    private void validate(CampaignRequest req, UUID self) {
        if (!Sources.ALL.contains(req.channel())) {
            throw ApiException.badRequest("Canal inconnu : " + req.channel());
        }
        LocalDate start = req.periodStart();
        LocalDate end = req.periodEnd();
        if (start != null && end != null && end.isBefore(start)) {
            throw ApiException.badRequest("La fin de la période précède son début.");
        }
        var params = new MapSqlParameterSource("channel", req.channel());
        var sql = new StringBuilder("select name from campaigns where channel = :channel");
        if (self != null) {
            sql.append(" and id <> :self");
            params.addValue("self", self);
        }
        if (end != null) {
            sql.append(" and (period_start is null or period_start <= :end)");
            params.addValue("end", end);
        }
        if (start != null) {
            sql.append(" and (period_end is null or period_end >= :start)");
            params.addValue("start", start);
        }
        List<String> overlapping = jdbc.queryForList(sql + " limit 1", params, String.class);
        if (!overlapping.isEmpty()) {
            throw ApiException.conflict("La période chevauche la campagne « " + overlapping.get(0)
                    + " » sur le même canal : ses leads seraient comptés deux fois.");
        }
    }

    private static CampaignRow row(ResultSet rs, int i) throws SQLException {
        BigDecimal spend = rs.getBigDecimal("spend_usd");
        BigDecimal revenue = rs.getBigDecimal("revenue");
        long leads = rs.getLong("leads");
        return new CampaignRow(rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("channel"), spend,
                rs.getLong("impressions"), rs.getLong("clicks"), rs.getObject("period_start", LocalDate.class),
                rs.getObject("period_end", LocalDate.class), leads, rs.getLong("won"), revenue,
                costPerLead(spend, leads), revenuePerUsd(revenue, spend));
    }

    private static BigDecimal sum(List<BigDecimal> values) {
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    static BigDecimal costPerLead(BigDecimal spend, long leads) {
        return leads == 0 ? null : spend.divide(BigDecimal.valueOf(leads), 2, RoundingMode.HALF_UP);
    }

    static BigDecimal revenuePerUsd(BigDecimal revenue, BigDecimal spend) {
        return spend.signum() == 0 ? null : revenue.divide(spend, 2, RoundingMode.HALF_UP);
    }
}
