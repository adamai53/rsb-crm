package dj.redseabunkering.crm.report;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import dj.redseabunkering.crm.activity.ActivityDto;
import dj.redseabunkering.crm.deal.DealDtos.DealRow;

public final class ReportDtos {

    private ReportDtos() {
    }

    public record Dashboard(String today, BigDecimal weightedPipelineUsd, BigDecimal openAmountUsd, long openCount,
            long requests30Days, long newLeads, int conversionRatePct, long wonCount, long lostCount,
            BigDecimal deliveredMt90Days, List<StageRow> byStage, List<LabelCount> bySource, List<DealRow> nextCalls,
            List<ActivityDto> recentActivity) {
    }

    public record StageRow(String code, String label, long count, BigDecimal amountUsd) {
    }

    public record LabelCount(String label, long count) {
    }

    /** {@code month} au format AAAA-MM. */
    public record MonthRevenue(String month, BigDecimal revenueUsd) {
    }

    public record FunnelStep(String code, String label, long count) {
    }

    public record ProductVolume(String code, String label, BigDecimal volumeMt) {
    }

    public record SalesPerformance(UUID userId, String name, String initials, long delivered, BigDecimal revenueUsd,
            int conversionRatePct, BigDecimal openAmountUsd) {
    }

    /** {@code averageDays} est nul tant qu'aucune transaction n'a été livrée. */
    public record SalesCycle(Integer averageDays, long delivered) {
    }
}
