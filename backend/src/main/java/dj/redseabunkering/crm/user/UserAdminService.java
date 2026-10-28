package dj.redseabunkering.crm.user;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dj.redseabunkering.crm.auth.AuthMailer;
import dj.redseabunkering.crm.auth.AuthService;
import dj.redseabunkering.crm.auth.RefreshTokenRepository;
import dj.redseabunkering.crm.common.ApiException;

/** Gestion des comptes par un ADMIN : validation, désactivation, rôle, renvoi de la vérification. */
@Service
public class UserAdminService {

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final AuthService auth;
    private final AuthMailer mailer;
    private final Clock clock;

    public UserAdminService(UserRepository users, RefreshTokenRepository refreshTokens, AuthService auth,
            AuthMailer mailer, Clock clock) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.auth = auth;
        this.mailer = mailer;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<User> list() {
        return users.findAllByOrderByStatusAscLastNameAscFirstNameAsc();
    }

    @Transactional
    public User approve(UUID id) {
        User user = get(id);
        if (user.getEmailVerifiedAt() == null) {
            throw ApiException.conflict("L'utilisateur n'a pas encore confirmé son adresse e-mail.");
        }
        if (user.getStatus() == UserStatus.ACTIVE) {
            return user;
        }
        user.setStatus(UserStatus.ACTIVE);
        mailer.sendAccountApproved(user);
        return user;
    }

    @Transactional
    public User disable(UUID id, UUID actorId) {
        User user = get(id);
        if (user.getId().equals(actorId)) {
            throw ApiException.conflict("Vous ne pouvez pas désactiver votre propre compte.");
        }
        user.setStatus(UserStatus.DISABLED);
        refreshTokens.revokeAllForUser(user, clock.instant());
        return user;
    }

    @Transactional
    public User changeRole(UUID id, Role role, UUID actorId) {
        User user = get(id);
        if (user.getId().equals(actorId) && role != Role.ADMIN) {
            throw ApiException.conflict("Vous ne pouvez pas retirer votre propre rôle d'administrateur.");
        }
        user.setRole(role);
        // Le nouveau rôle s'applique au prochain access token : on force une reconnexion.
        refreshTokens.revokeAllForUser(user, clock.instant());
        return user;
    }

    @Transactional
    public void resendVerification(UUID id) {
        User user = get(id);
        if (user.getEmailVerifiedAt() != null) {
            throw ApiException.conflict("Cette adresse e-mail est déjà confirmée.");
        }
        auth.sendEmailVerification(user);
    }

    private User get(UUID id) {
        return users.findById(id).orElseThrow(() -> ApiException.notFound("Utilisateur introuvable."));
    }
}
