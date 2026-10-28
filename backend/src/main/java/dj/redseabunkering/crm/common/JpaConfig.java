package dj.redseabunkering.crm.common;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/** Audit JPA : l'auteur d'une modification est l'utilisateur du jeton JWT courant. */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "currentAuditor")
public class JpaConfig {

    @Bean
    AuditorAware<UUID> currentAuditor() {
        return () -> Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
                .map(auth -> auth.getPrincipal() instanceof Jwt jwt ? jwt.getSubject() : null)
                .map(UUID::fromString);
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
