package dj.redseabunkering.crm.deal;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import dj.redseabunkering.crm.deal.PipelineMetrics.OpenAmount;

/** Règles 1 (pipeline pondéré) et 2 (taux de transformation). */
class PipelineMetricsTests {

    @Test
    void weightedPipelineSumsAmountTimesProbability() {
        var open = List.of(
                new OpenAmount(new BigDecimal("520200"), 70),   // 850 MT × 612 en négociation
                new OpenAmount(new BigDecimal("253500"), 50),   // 300 MT × 845 en cotation
                new OpenAmount(new BigDecimal("100000"), 10));
        assertThat(PipelineMetrics.weightedPipeline(open)).isEqualByComparingTo("500890.00");
        assertThat(PipelineMetrics.weightedPipeline(List.of())).isEqualByComparingTo("0");
    }

    @Test
    void conversionRateIsWonOverClosed() {
        assertThat(PipelineMetrics.conversionRate(3, 1)).isEqualTo(75);
        assertThat(PipelineMetrics.conversionRate(2, 1)).isEqualTo(67);
        assertThat(PipelineMetrics.conversionRate(0, 0)).isZero();
        assertThat(PipelineMetrics.conversionRate(0, 4)).isZero();
    }
}
