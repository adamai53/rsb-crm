package dj.redseabunkering.crm.deal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;

/** Indicateurs du pipeline (règles 1 et 2), en fonctions pures pour être testés isolément. */
public final class PipelineMetrics {

    private PipelineMetrics() {
    }

    /** Règle 1 : Σ (montant des transactions ouvertes × probabilité de l'étape). */
    public static BigDecimal weightedPipeline(Collection<OpenAmount> openDeals) {
        return openDeals.stream()
                .map(d -> d.amountUsd().multiply(BigDecimal.valueOf(d.probability())).divide(BigDecimal.valueOf(100)))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /** Règle 2 : livrées / (livrées + perdues), en pourcentage entier ; 0 sans transaction clôturée. */
    public static int conversionRate(long won, long lost) {
        long closed = won + lost;
        return closed == 0 ? 0 : (int) Math.round(won * 100.0 / closed);
    }

    public record OpenAmount(BigDecimal amountUsd, int probability) {
    }
}
