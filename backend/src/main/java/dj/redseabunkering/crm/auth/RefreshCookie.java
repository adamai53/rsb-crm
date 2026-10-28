package dj.redseabunkering.crm.auth;

import java.time.Duration;
import java.util.Optional;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/** Cookie du refresh token : HttpOnly, Secure, SameSite=Strict, limité aux routes /api/auth. */
@Component
public class RefreshCookie {

    public static final String NAME = "rsb_refresh";
    private static final String PATH = "/api/auth";

    private final AuthProperties props;

    public RefreshCookie(AuthProperties props) {
        this.props = props;
    }

    public ResponseCookie create(String token) {
        return build(token, props.refreshTtl());
    }

    public ResponseCookie clear() {
        return build("", Duration.ZERO);
    }

    public Optional<String> read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        for (Cookie c : cookies) {
            if (NAME.equals(c.getName()) && !c.getValue().isBlank()) {
                return Optional.of(c.getValue());
            }
        }
        return Optional.empty();
    }

    private ResponseCookie build(String value, Duration maxAge) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(props.cookieSecure())
                .sameSite("Strict")
                .path(PATH)
                .maxAge(maxAge)
                .build();
    }
}
