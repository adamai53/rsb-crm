package dj.redseabunkering.crm.campaign;

import java.time.Duration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import dj.redseabunkering.crm.common.ApiException;

/**
 * Vérification du jeton Cloudflare Turnstile. Sans CAPTCHA_SECRET le captcha est désactivé (développement) ;
 * si Turnstile est injoignable, la demande est refusée : l'utilisateur peut réessayer, un robot ne passe pas.
 */
@Component
public class CaptchaVerifier {

    private static final Logger log = LoggerFactory.getLogger(CaptchaVerifier.class);

    private final PublicFormProperties props;
    private final RestClient http;

    public CaptchaVerifier(PublicFormProperties props) {
        this.props = props;
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.http = RestClient.builder().requestFactory(factory).build();
        if (!props.captchaEnabled()) {
            log.warn("CAPTCHA_SECRET absent : le formulaire public n'est pas protégé par un captcha.");
        }
    }

    public boolean verify(String token, String ip) {
        if (!props.captchaEnabled()) {
            return true;
        }
        if (token == null || token.isBlank()) {
            return false;
        }
        var form = new LinkedMultiValueMap<String, String>();
        form.add("secret", props.captchaSecret());
        form.add("response", token);
        if (ip != null) {
            form.add("remoteip", ip);
        }
        try {
            Result result = http.post().uri(props.captchaVerifyUrl()).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form).retrieve().body(Result.class);
            return result != null && result.success();
        } catch (RestClientException ex) {
            log.warn("Vérification du captcha impossible : {}", ex.getMessage());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Vérification momentanément indisponible. Réessayez.");
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Result(boolean success) {
    }
}
