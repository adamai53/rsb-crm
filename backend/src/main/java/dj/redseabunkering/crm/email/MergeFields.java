package dj.redseabunkering.crm.email;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Règle 13 : remplace {{prenom}}, {{entreprise}}, {{navire}}… ; un champ inconnu ou vide disparaît. */
public final class MergeFields {

    private static final Pattern FIELD = Pattern.compile("\\{\\{(\\w+)\\}\\}");

    private MergeFields() {
    }

    public static String merge(String template, Map<String, String> values) {
        return FIELD.matcher(template).replaceAll(
                m -> Matcher.quoteReplacement(values.getOrDefault(m.group(1), "")));
    }
}
