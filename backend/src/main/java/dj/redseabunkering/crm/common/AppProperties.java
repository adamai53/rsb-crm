package dj.redseabunkering.crm.common;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Paramètres applicatifs lus depuis les variables d'environnement (préfixe {@code app}). */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String publicUrl,
        List<String> corsAllowedOrigins,
        boolean signupRequiresApproval,
        boolean seedDemo,
        String mailFrom) {

    public AppProperties {
        corsAllowedOrigins = corsAllowedOrigins == null ? List.of() : List.copyOf(corsAllowedOrigins);
    }
}
