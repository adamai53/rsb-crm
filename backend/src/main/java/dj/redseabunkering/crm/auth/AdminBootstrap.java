package dj.redseabunkering.crm.auth;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dj.redseabunkering.crm.user.Role;
import dj.redseabunkering.crm.user.User;
import dj.redseabunkering.crm.user.UserRepository;
import dj.redseabunkering.crm.user.UserStatus;

/** Crée le premier administrateur depuis APP_ADMIN_EMAIL / APP_ADMIN_PASSWORD si aucun admin n'existe. */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuthProperties props;
    private final Clock clock;

    public AdminBootstrap(UserRepository users, PasswordEncoder passwordEncoder, AuthProperties props, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.props = props;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.existsByRole(Role.ADMIN)) {
            return;
        }
        String email = props.adminEmail();
        String password = props.adminPassword();
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            log.warn("Aucun administrateur : définissez APP_ADMIN_EMAIL et APP_ADMIN_PASSWORD pour en créer un.");
            return;
        }
        if (users.findByEmail(email).isPresent()) {
            log.warn("APP_ADMIN_EMAIL correspond à un compte existant non administrateur : aucun admin créé.");
            return;
        }
        User admin = new User(email, passwordEncoder.encode(password), "Administrateur", "RSB");
        admin.setRole(Role.ADMIN);
        admin.markEmailVerified(clock.instant(), false);
        admin.setStatus(UserStatus.ACTIVE);
        users.save(admin);
        log.info("Compte administrateur initial créé pour {}", admin.getEmail());
    }
}
