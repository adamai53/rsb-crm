package dj.redseabunkering.crm.campaign;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Formulaire public du site : clé API, captcha Turnstile, origines autorisées et limitation de débit. */
@ConfigurationProperties(prefix = "app.public-form")
public record PublicFormProperties(
        String apiKey,
        String captchaSecret,
        String captchaSiteKey,
        String captchaVerifyUrl,
        List<String> allowedOrigins,
        int maxPerIp,
        Duration window) {

    public PublicFormProperties {
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
    }

    public boolean captchaEnabled() {
        return captchaSecret != null && !captchaSecret.isBlank();
    }
}
