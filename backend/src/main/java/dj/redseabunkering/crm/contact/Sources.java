package dj.redseabunkering.crm.contact;

import java.util.List;
import java.util.Map;

/** Origines d'un contact ou d'une demande, avec leur poids dans le score (règle 11). */
public final class Sources {

    /** Ordre d'affichage du prototype. */
    public static final List<String> ALL = List.of("Site web", "Google Ads", "LinkedIn", "Meta Ads",
            "Recommandation", "Salon / événement", "Appel entrant", "E-mail direct");

    public static final Map<String, Integer> SCORE = Map.of(
            "Recommandation", 20,
            "Appel entrant", 18,
            "Site web", 15,
            "Salon / événement", 15,
            "Google Ads", 12,
            "LinkedIn", 10,
            "E-mail direct", 8,
            "Meta Ads", 6);

    /** Points d'une source inconnue ou absente. */
    public static final int OTHER_POINTS = 5;

    private Sources() {
    }
}
