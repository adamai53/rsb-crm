package dj.redseabunkering.crm.auth;

import java.time.Clock;
import java.time.Duration;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Purge quotidienne des refresh tokens expirés depuis plus de 30 jours. */
@Component
public class RefreshTokenCleanup {

    private final RefreshTokenRepository refreshTokens;
    private final Clock clock;

    public RefreshTokenCleanup(RefreshTokenRepository refreshTokens, Clock clock) {
        this.refreshTokens = refreshTokens;
        this.clock = clock;
    }

    @Scheduled(cron = "0 30 3 * * *", zone = "Africa/Djibouti")
    @Transactional
    public void purge() {
        refreshTokens.deleteExpired(clock.instant().minus(Duration.ofDays(30)));
    }
}
