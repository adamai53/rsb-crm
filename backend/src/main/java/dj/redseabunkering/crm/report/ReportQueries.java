package dj.redseabunkering.crm.report;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import dj.redseabunkering.crm.common.BusinessCalendar;
import dj.redseabunkering.crm.contact.Lifecycle;
import dj.redseabunkering.crm.deal.PipelineMetrics;
import dj.redseabunkering.crm.report.ReportDtos.FunnelStep;
import dj.redseabunkering.crm.report.ReportDtos.LabelCount;
import dj.redseabunkering.crm.report.ReportDtos.MonthRevenue;
import dj.redseabunkering.crm.report.ReportDtos.ProductVolume;
import dj.redseabunkering.crm.report.ReportDtos.SalesCycle;
import dj.redseabunkering.crm.report.ReportDtos.SalesPerformance;

/** Rapports : {@code owner} non nul limite le résultat au périmètre d'un commercial. */
@Component
public class ReportQueries {

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final int MONTHS = 6;

    private final NamedParameterJdbcTemplate jdbc;
    private final BusinessCalendar calendar;

    public ReportQueries(NamedParameterJdbcTemplate jdbc, BusinessCalendar calendar) {
        this.jdbc = jdbc;
        this.calendar = calendar;
    }

    /** Six derniers mois, mois en cours compris, y compris ceux sans livraison. */
    public List<MonthRevenue> monthlyRevenue(UUID owner) {
        LocalDate first = firstMonth();
        Map<String, BigDecimal> byMonth = new java.util.HashMap<>();
        jdbc.query("""
                select to_char(d.closed_at, 'YYYY-MM') as m, sum(d.amount_usd) as revenue
                from deals d join pipeline_stages s on s.code = d.stage
                where s.is_won and d.closed_at >= :from""" + scope("d.owner_id", owner) + " group by 1",
                params(owner).addValue("from", first),
                rs -> {
                    byMonth.put(rs.getString("m"), rs.getBigDecimal("revenue"));
                });
        return IntStream.range(0, MONTHS).mapToObj(first::plusMonths).map(MONTH::format)
                .map(m -> new MonthRevenue(m, byMonth.getOrDefault(m, BigDecimal.ZERO))).toList();
    }

    /** Contacts ayant atteint au moins chaque étape du cycle de vie. */
    public List<FunnelStep> funnel(UUID owner) {
        Map<String, Long> counts = jdbc.query(
                "select lifecycle, count(*) as n from contacts where true" + scope("owner_id", owner) + " group by 1",
                params(owner), (rs, i) -> Map.entry(rs.getString("lifecycle"), rs.getLong("n")))
                .stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        return funnelFrom(counts);
    }

    static List<FunnelStep> funnelFrom(Map<String, Long> counts) {
        Lifecycle[] levels = Lifecycle.values();
        var steps = new ArrayList<FunnelStep>();
        for (int i = 0; i < levels.length; i++) {
            long reached = 0;
            for (int j = i; j < levels.length; j++) {
                reached += counts.getOrDefault(levels[j].name(), 0L);
            }
            steps.add(new FunnelStep(levels[i].name(), levels[i].label(), reached));
        }
        return steps;
    }

    public List<ProductVolume> productVolumes(UUID owner) {
        return jdbc.query("""
                select g.code, g.label, coalesce(sum(d.quantity_mt) filter (where s.is_won and d.closed_at >= :from"""
                + scope("d.owner_id", owner) + """
                ), 0) as volume
                from fuel_grades g
                left join deals d on d.fuel_grade = g.code
                left join pipeline_stages s on s.code = d.stage
                group by g.code, g.label, g.sort_order
                order by g.sort_order""",
                params(owner).addValue("from", firstMonth()),
                (rs, i) -> new ProductVolume(rs.getString("code"), rs.getString("label"), rs.getBigDecimal("volume")));
    }

    public List<LabelCount> lostReasons(UUID owner) {
        return jdbc.query("""
                select coalesce(d.lost_reason, 'Non renseigné') as reason, count(*) as n
                from deals d join pipeline_stages s on s.code = d.stage
                where s.is_lost""" + scope("d.owner_id", owner) + " group by 1 order by n desc, reason",
                params(owner), (rs, i) -> new LabelCount(rs.getString("reason"), rs.getLong("n")));
    }

    /** Commerciaux actifs et toute personne encore propriétaire d'une transaction. */
    public List<SalesPerformance> salesPerformance(UUID owner) {
        return jdbc.query("""
                select u.id, u.first_name || ' ' || u.last_name as name, u.initials,
                       count(*) filter (where s.is_won) as won,
                       count(*) filter (where s.is_lost) as lost,
                       coalesce(sum(d.amount_usd) filter (where s.is_won), 0) as revenue,
                       coalesce(sum(d.amount_usd) filter (where not s.is_won and not s.is_lost), 0) as open_amount
                from users u
                left join deals d on d.owner_id = u.id
                left join pipeline_stages s on s.code = d.stage
                where ((u.role = 'SALES' and u.status = 'ACTIVE') or d.id is not null)"""
                + scope("u.id", owner) + """

                group by u.id, u.first_name, u.last_name, u.initials
                order by revenue desc, name""",
                params(owner),
                (rs, i) -> new SalesPerformance(rs.getObject("id", UUID.class), rs.getString("name"),
                        rs.getString("initials"), rs.getLong("won"), rs.getBigDecimal("revenue"),
                        PipelineMetrics.conversionRate(rs.getLong("won"), rs.getLong("lost")),
                        rs.getBigDecimal("open_amount")));
    }

    /** Jours entre la création de la transaction et sa livraison. */
    public SalesCycle salesCycle(UUID owner) {
        return jdbc.queryForObject("""
                select round(avg(d.closed_at - (d.created_at at time zone 'Africa/Djibouti')::date)) as days, count(*) as n
                from deals d join pipeline_stages s on s.code = d.stage
                where s.is_won and d.closed_at is not null""" + scope("d.owner_id", owner),
                params(owner), (rs, i) -> {
                    BigDecimal days = rs.getBigDecimal("days");
                    return new SalesCycle(days == null ? null : days.intValue(), rs.getLong("n"));
                });
    }

    private LocalDate firstMonth() {
        return calendar.today().withDayOfMonth(1).minusMonths(MONTHS - 1L);
    }

    private static String scope(String column, UUID owner) {
        return owner == null ? "" : " and " + column + " = :owner";
    }

    private static MapSqlParameterSource params(UUID owner) {
        var params = new MapSqlParameterSource();
        if (owner != null) {
            params.addValue("owner", owner);
        }
        return params;
    }
}
