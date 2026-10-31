package dj.redseabunkering.crm.campaign;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import dj.redseabunkering.crm.common.ApiException;

/**
 * Contrôles d'entrée du formulaire public : limitation de débit par adresse IP (en mémoire, le backend tourne en une
 * seule instance), puis clé API. Sans clé configurée, le formulaire est fermé plutôt qu'ouvert à tous.
 */
@Component
public class PublicFormGuard {

    /** Au-delà, les fenêtres expirées sont purgées pour borner la mémoire. */
    private static final int PURGE_ABOVE = 10_000;

    private final PublicFormProperties props;
    private final Clock clock;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public PublicFormGuard(PublicFormProperties props, Clock clock) {
        this.props = props;
        this.clock = clock;
    }

    public void check(String apiKey, String ip) {
        throttle(ip);
        String expected = props.apiKey();
        if (expected == null || expected.isBlank()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Le formulaire n'est pas encore ouvert.");
        }
        if (apiKey == null || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                apiKey.getBytes(StandardCharsets.UTF_8))) {
            throw ApiException.unauthorized("Clé API invalide.");
        }
    }

    private void throttle(String ip) {
        Instant now = clock.instant();
        if (windows.size() > PURGE_ABOVE) {
            windows.values().removeIf(w -> !w.end().isAfter(now));
        }
        Window window = windows.compute(ip == null ? "?" : ip, (k, w) -> w == null || !w.end().isAfter(now)
                ? new Window(now.plus(props.window()), 1)
                : new Window(w.end(), w.count() + 1));
        if (window.count() > props.maxPerIp()) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Trop de demandes envoyées. Réessayez plus tard.");
        }
    }

    private record Window(Instant end, int count) {
    }
}
