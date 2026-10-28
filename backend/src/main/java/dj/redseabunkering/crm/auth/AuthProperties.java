package dj.redseabunkering.crm.auth;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Paramètres d'authentification (préfixe {@code app.auth}). */
@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(
        String jwtSecret,
        Duration accessTtl,
        Duration refreshTtl,
        boolean cookieSecure,
        Duration lockDuration,
        Duration emailVerificationTtl,
        Duration passwordResetTtl,
        String adminEmail,
        String adminPassword) {
}
