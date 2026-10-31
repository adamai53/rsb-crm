package dj.redseabunkering.crm.report;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dj.redseabunkering.crm.activity.ActivityQueries;
import dj.redseabunkering.crm.common.BusinessCalendar;
import dj.redseabunkering.crm.common.CurrentUser;
import dj.redseabunkering.crm.deal.DealQueries;
import dj.redseabunkering.crm.deal.PipelineMetrics;
import dj.redseabunkering.crm.deal.PipelineMetrics.OpenAmount;
import dj.redseabunkering.crm.report.ReportDtos.Dashboard;
import dj.redseabunkering.crm.report.ReportDtos.FunnelStep;
import dj.redseabunkering.crm.report.ReportDtos.LabelCount;
import dj.redseabunkering.crm.report.ReportDtos.MonthRevenue;
import dj.redseabunkering.crm.report.ReportDtos.ProductVolume;
import dj.redseabunkering.crm.report.ReportDtos.SalesCycle;
import dj.redseabunkering.crm.report.ReportDtos.SalesPerformance;
import dj.redseabunkering.crm.report.ReportDtos.StageRow;

/**
 * Tableau de bord (vue d'équipe, tout le monde voit tout en lecture) et rapports : complets pour MANAGER et ADMIN,
 * limités à son périmètre pour un SALES.
 */
@RestController
@RequestMapping("/api")
public class ReportController {

    private final ReportQueries reports;
    private final DealQueries deals;
    private final ActivityQueries activities;
    private final BusinessCalendar calendar;
    private final NamedParameterJdbcTemplate jdbc;

    public ReportController(ReportQueries reports, DealQueries deals, ActivityQueries activities,
            BusinessCalendar calendar, NamedParameterJdbcTemplate jdbc) {
        this.reports = reports;
        this.deals = deals;
        this.activities = activities;
        this.calendar = calendar;
        this.jdbc = jdbc;
    }

    @GetMapping("/dashboard")
    public Dashboard dashboard() {
        var today = calendar.today();
        record Stage(StageRow row, int probability) {
        }
        List<Stage> stages = jdbc.query("""
                select s.code, s.label, s.probability, count(d.id) as n, coalesce(sum(d.amount_usd), 0) as amount
                from pipeline_stages s left join deals d on d.stage = s.code
                where not s.is_won and not s.is_lost
                group by s.code, s.label, s.probability, s.sort_order order by s.sort_order""",
                (rs, i) -> new Stage(new StageRow(rs.getString("code"), rs.getString("label"), rs.getLong("n"),
                        rs.getBigDecimal("amount")), rs.getInt("probability")));
        BigDecimal weighted = PipelineMetrics.weightedPipeline(
                stages.stream().map(s -> new OpenAmount(s.row().amountUsd(), s.probability())).toList());
        BigDecimal openAmount = stages.stream().map(s -> s.row().amountUsd()).reduce(BigDecimal.ZERO, BigDecimal::add);
        long openCount = stages.stream().mapToLong(s -> s.row().count()).sum();

        var since = Timestamp.from(today.minusDays(30).atStartOfDay(BusinessCalendar.ZONE).toInstant());
        var counts = jdbc.queryForMap("""
                select (select count(*) from deals where created_at >= :since) as recent_deals,
                       (select count(*) from leads where status = 'NOUVEAU' and received_at >= :since) as recent_leads,
                       (select count(*) from leads where status = 'NOUVEAU') as new_leads,
                       (select count(*) from deals d join pipeline_stages s on s.code = d.stage where s.is_won) as won,
                       (select count(*) from deals d join pipeline_stages s on s.code = d.stage where s.is_lost) as lost,
                       (select coalesce(sum(d.quantity_mt), 0) from deals d join pipeline_stages s on s.code = d.stage
                        where s.is_won and d.closed_at >= :from90) as volume""",
                Map.of("since", since, "from90", today.minus(90, ChronoUnit.DAYS)));
        long won = ((Number) counts.get("won")).longValue();
        long lost = ((Number) counts.get("lost")).longValue();

        var bySource = jdbc.query("""
                select source, count(*) as n from deals where source is not null
                group by source order by n desc, source""",
                (rs, i) -> new LabelCount(rs.getString("source"), rs.getLong("n")));
        var nextCalls = deals.upcomingCalls(today, 7).stream()
                .filter(d -> !d.won() && !d.eta().isBefore(today)).toList();

        return new Dashboard(today.toString(), weighted, openAmount, openCount,
                ((Number) counts.get("recent_deals")).longValue() + ((Number) counts.get("recent_leads")).longValue(),
                ((Number) counts.get("new_leads")).longValue(), PipelineMetrics.conversionRate(won, lost), won, lost,
                (BigDecimal) counts.get("volume"), stages.stream().map(Stage::row).toList(), bySource, nextCalls,
                activities.recent(6));
    }

    @GetMapping("/reports/monthly-revenue")
    public List<MonthRevenue> monthlyRevenue(CurrentUser me) {
        return reports.monthlyRevenue(scope(me));
    }

    @GetMapping("/reports/funnel")
    public List<FunnelStep> funnel(CurrentUser me) {
        return reports.funnel(scope(me));
    }

    @GetMapping("/reports/product-volumes")
    public List<ProductVolume> productVolumes(CurrentUser me) {
        return reports.productVolumes(scope(me));
    }

    @GetMapping("/reports/lost-reasons")
    public List<LabelCount> lostReasons(CurrentUser me) {
        return reports.lostReasons(scope(me));
    }

    @GetMapping("/reports/sales-performance")
    public List<SalesPerformance> salesPerformance(CurrentUser me) {
        return reports.salesPerformance(scope(me));
    }

    @GetMapping("/reports/sales-cycle")
    public SalesCycle salesCycle(CurrentUser me) {
        return reports.salesCycle(scope(me));
    }

    private static UUID scope(CurrentUser me) {
        return me.canManageAll() ? null : me.id();
    }
}
