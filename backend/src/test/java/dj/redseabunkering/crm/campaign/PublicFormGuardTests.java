package dj.redseabunkering.crm.campaign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import dj.redseabunkering.crm.common.ApiException;

/** Clé API, limitation de débit et captcha, sans démarrer l'application. */
class PublicFormGuardTests {

    private static final Instant NOW = Instant.parse("2026-10-31T10:00:00Z");

    private static PublicFormProperties props(String key, String secret, String url) {
        return new PublicFormProperties(key, secret, "site", url, List.of(), 2, Duration.ofMinutes(10));
    }

    private static HttpStatus statusOf(Runnable call) {
        try {
            call.run();
            return null;
        } catch (ApiException e) {
            return e.getStatus();
        }
    }

    @Test
    void closedWithoutConfiguredKey() {
        var guard = new PublicFormGuard(props("", null, null), Clock.fixed(NOW, ZoneOffset.UTC));
        assertThat(statusOf(() -> guard.check("n'importe", "1.1.1.1"))).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void rejectsWrongKey() {
        var guard = new PublicFormGuard(props("secret", null, null), Clock.fixed(NOW, ZoneOffset.UTC));
        assertThat(statusOf(() -> guard.check("autre", "1.1.1.1"))).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(statusOf(() -> guard.check(null, "1.1.1.2"))).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void throttlesPerIpThenRecovers() {
        var clock = new MutableClock();
        var guard = new PublicFormGuard(props("secret", null, null), clock);
        assertThatCode(() -> guard.check("secret", "1.1.1.1")).doesNotThrowAnyException();
        assertThatCode(() -> guard.check("secret", "1.1.1.1")).doesNotThrowAnyException();
        assertThat(statusOf(() -> guard.check("secret", "1.1.1.1"))).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        // Une autre adresse n'est pas touchée
        assertThatCode(() -> guard.check("secret", "2.2.2.2")).doesNotThrowAnyException();
        clock.now = NOW.plus(Duration.ofMinutes(10));
        assertThatCode(() -> guard.check("secret", "1.1.1.1")).doesNotThrowAnyException();
    }

    @Test
    void captchaDisabledWithoutSecret() {
        assertThat(new CaptchaVerifier(props("k", "", null)).verify(null, "1.1.1.1")).isTrue();
    }

    @Test
    void captchaFollowsProviderAnswer() throws IOException {
        var received = new StringBuilder();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/verify", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            received.append(body);
            byte[] answer = ("{\"success\":" + body.contains("response=bon") + ",\"hostname\":\"x\"}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, answer.length);
            exchange.getResponseBody().write(answer);
            exchange.close();
        });
        server.start();
        try {
            var verifier = new CaptchaVerifier(
                    props("k", "s3cret", "http://127.0.0.1:" + server.getAddress().getPort() + "/verify"));
            assertThat(verifier.verify("bon", "1.1.1.1")).isTrue();
            assertThat(received).contains("secret=s3cret");
            assertThat(verifier.verify("mauvais", "1.1.1.1")).isFalse();
            assertThat(verifier.verify(null, "1.1.1.1")).isFalse();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void captchaUnreachableRefusesTheRequest() {
        var verifier = new CaptchaVerifier(props("k", "s", "http://127.0.0.1:1/verify"));
        assertThatThrownBy(() -> verifier.verify("x", "1.1.1.1")).isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    private static final class MutableClock extends Clock {
        Instant now = NOW;

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
