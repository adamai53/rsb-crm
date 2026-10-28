package dj.redseabunkering.crm.contact;

import java.math.BigDecimal;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Score d'un contact sur 100 (règle métier 11), détail par critère :
 * source, cycle de vie, interactions, transactions ouvertes, poste de décideur carburant.
 */
public final class ContactScoring {

    static final Pattern DECISION_MAKER = Pattern.compile("buyer|achat|procurement|soutage|fuel|trader",
            Pattern.CASE_INSENSITIVE);
    private static final BigDecimal HIGH_OPEN = BigDecimal.valueOf(400_000);
    private static final BigDecimal MID_OPEN = BigDecimal.valueOf(100_000);

    private ContactScoring() {
    }

    public static Score score(String source, Lifecycle lifecycle, String title, long activityCount,
            BigDecimal openDealsAmount) {
        int sourcePoints = source == null ? Sources.OTHER_POINTS : Sources.SCORE.getOrDefault(source, Sources.OTHER_POINTS);
        int lifecyclePoints = lifecycle.ordinal() * 7;
        int interactionPoints = (int) Math.min(activityCount * 5, 20);
        BigDecimal open = openDealsAmount == null ? BigDecimal.ZERO : openDealsAmount;
        int openPoints = open.compareTo(HIGH_OPEN) > 0 ? 18 : open.compareTo(MID_OPEN) > 0 ? 12 : open.signum() > 0 ? 6 : 0;
        int rolePoints = title != null && DECISION_MAKER.matcher(title).find() ? 12 : 0;

        var parts = List.of(
                new Part("Source : " + (source == null || source.isBlank() ? "non renseignée" : source), sourcePoints),
                new Part("Cycle de vie : " + lifecycle.label(), lifecyclePoints),
                new Part("Interactions récentes", interactionPoints),
                new Part("Transactions ouvertes", openPoints),
                new Part("Décideur achats carburant", rolePoints));
        int total = Math.min(100, parts.stream().mapToInt(Part::points).sum());
        return new Score(total, parts);
    }

    public record Score(int total, List<Part> parts) {
    }

    public record Part(String label, int points) {
    }
}
