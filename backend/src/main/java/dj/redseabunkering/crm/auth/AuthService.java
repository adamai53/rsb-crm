package dj.redseabunkering.crm.auth;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dj.redseabunkering.crm.auth.AuthDtos.LoginRequest;
import dj.redseabunkering.crm.auth.AuthDtos.RegisterRequest;
import dj.redseabunkering.crm.common.ApiException;
import dj.redseabunkering.crm.common.AppProperties;
import dj.redseabunkering.crm.common.SecureTokens;
import dj.redseabunkering.crm.user.Role;
import dj.redseabunkering.crm.user.User;
import dj.redseabunkering.crm.user.UserRepository;
import dj.redseabunkering.crm.user.UserStatus;

/**
 * Parcours de compte : inscription, vérification d'e-mail, connexion, rotation des refresh tokens,
 * déconnexion et réinitialisation du mot de passe. Les messages d'erreur ne révèlent pas l'existence d'un compte.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    static final String BAD_CREDENTIALS = "E-mail ou mot de passe incorrect.";
    static final String INVALID_SESSION = "Session expirée. Veuillez vous reconnecter.";

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final EmailVerificationTokenRepository verificationTokens;
    private final PasswordResetTokenRepository resetTokens;
    private final LoginEventRepository loginEvents;
    private final PasswordEncoder passwordEncoder;
    private final AccessTokenService accessTokens;
    private final AuthMailer mailer;
    private final AuthProperties props;
    private final AppProperties app;
    private final Clock clock;
    /** Hash factice : le temps de réponse est le même que l'e-mail existe ou non. */
    private final String dummyHash;

    public AuthService(UserRepository users, RefreshTokenRepository refreshTokens,
            EmailVerificationTokenRepository verificationTokens, PasswordResetTokenRepository resetTokens,
            LoginEventRepository loginEvents, PasswordEncoder passwordEncoder, AccessTokenService accessTokens,
            AuthMailer mailer, AuthProperties props, AppProperties app, Clock clock) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.verificationTokens = verificationTokens;
        this.resetTokens = resetTokens;
        this.loginEvents = loginEvents;
        this.passwordEncoder = passwordEncoder;
        this.accessTokens = accessTokens;
        this.mailer = mailer;
        this.props = props;
        this.app = app;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode(SecureTokens.generate());
    }

    // ------------------------------------------------------------------ inscription

    @Transactional
    public void register(RegisterRequest req) {
        var existing = users.findByEmail(req.email());
        if (existing.isPresent()) {
            // Même réponse qu'une inscription normale ; le titulaire est prévenu par e-mail.
            mailer.sendAccountAlreadyExists(existing.get());
            return;
        }
        User user = users.save(new User(req.email(), passwordEncoder.encode(req.password()),
                req.firstName(), req.lastName()));
        sendEmailVerification(user);
    }

    @Transactional
    public void sendEmailVerification(User user) {
        Instant now = clock.instant();
        verificationTokens.invalidateAll(user, now);
        String token = SecureTokens.generate();
        verificationTokens.save(new EmailVerificationToken(user, SecureTokens.hash(token),
                now.plus(props.emailVerificationTtl())));
        mailer.sendEmailVerification(user, token);
    }

    @Transactional
    public UserStatus verifyEmail(String token) {
        Instant now = clock.instant();
        var stored = verificationTokens.findByTokenHash(SecureTokens.hash(token))
                .filter(t -> t.isUsable(now))
                .orElseThrow(() -> ApiException.badRequest("Ce lien de vérification est invalide ou a expiré."));
        stored.markUsed(now);
        User user = stored.getUser();
        user.markEmailVerified(now, app.signupRequiresApproval());
        if (user.getStatus() == UserStatus.PENDING_APPROVAL) {
            users.findByRoleAndStatus(Role.ADMIN, UserStatus.ACTIVE)
                    .forEach(admin -> mailer.sendApprovalRequest(admin, user));
        }
        return user.getStatus();
    }

    // ------------------------------------------------------------------ connexion

    /** Les compteurs d'échec doivent être enregistrés même quand la connexion est refusée. */
    @Transactional(noRollbackFor = ApiException.class)
    public Session login(LoginRequest req, ClientInfo client) {
        Instant now = clock.instant();
        String email = User.normalizeEmail(req.email());
        User user = users.findByEmail(email).orElse(null);

        if (user == null) {
            passwordEncoder.matches(req.password(), dummyHash);
            loginEvents.save(new LoginEvent(null, email, false, client, now));
            throw ApiException.unauthorized(BAD_CREDENTIALS);
        }
        if (user.isLocked(now)) {
            loginEvents.save(new LoginEvent(user.getId(), email, false, client, now));
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    "Trop de tentatives de connexion. Réessayez dans quelques minutes.");
        }
        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            if (user.registerFailedLogin(now, props.lockDuration())) {
                log.warn("Compte {} bloqué temporairement après {} échecs", user.getId(), User.MAX_FAILED_LOGINS);
            }
            loginEvents.save(new LoginEvent(user.getId(), email, false, client, now));
            throw ApiException.unauthorized(BAD_CREDENTIALS);
        }
        // Mot de passe correct : on peut expliquer pourquoi le compte n'est pas utilisable.
        switch (user.getStatus()) {
            case PENDING -> throw ApiException.forbidden(
                    "Votre adresse e-mail n'est pas encore vérifiée. Consultez le lien reçu par e-mail.");
            case PENDING_APPROVAL -> throw ApiException.forbidden(
                    "Votre compte est en attente de validation par un administrateur.");
            case DISABLED -> throw ApiException.forbidden("Ce compte est désactivé.");
            case ACTIVE -> { }
        }
        user.registerSuccessfulLogin(now);
        loginEvents.save(new LoginEvent(user.getId(), email, true, client, now));
        return openSession(user, UUID.randomUUID(), client);
    }

    /** Échange un refresh token contre une nouvelle paire ; un jeton déjà utilisé révoque toute la famille. */
    @Transactional(noRollbackFor = ApiException.class)
    public Session refresh(String rawToken, ClientInfo client) {
        Instant now = clock.instant();
        RefreshToken current = refreshTokens.findByTokenHash(SecureTokens.hash(rawToken))
                .orElseThrow(() -> ApiException.unauthorized(INVALID_SESSION));
        if (current.isRevoked()) {
            refreshTokens.revokeFamily(current.getFamilyId(), now);
            log.warn("Réutilisation d'un refresh token révoqué (utilisateur {}) : session fermée",
                    current.getUser().getId());
            throw ApiException.unauthorized(INVALID_SESSION);
        }
        if (current.isExpired(now) || current.getUser().getStatus() != UserStatus.ACTIVE) {
            current.revoke(now, null);
            throw ApiException.unauthorized(INVALID_SESSION);
        }
        Session next = openSession(current.getUser(), current.getFamilyId(), client);
        current.revoke(now, next.refreshTokenId());
        return next;
    }

    @Transactional
    public void logout(String rawToken) {
        refreshTokens.findByTokenHash(SecureTokens.hash(rawToken))
                .ifPresent(t -> refreshTokens.revokeFamily(t.getFamilyId(), clock.instant()));
    }

    /** Famille de session du cookie courant, pour la conserver lors d'un changement de mot de passe. */
    @Transactional(readOnly = true)
    public UUID sessionFamily(String rawToken) {
        return refreshTokens.findByTokenHash(SecureTokens.hash(rawToken)).map(RefreshToken::getFamilyId).orElse(null);
    }

    private Session openSession(User user, UUID familyId, ClientInfo client) {
        String raw = SecureTokens.generate();
        RefreshToken token = refreshTokens.save(new RefreshToken(user, SecureTokens.hash(raw), familyId,
                clock.instant().plus(props.refreshTtl()), client.userAgent(), client.ip()));
        return new Session(user, accessTokens.issue(user), raw, token.getId());
    }

    // ------------------------------------------------------------------ mot de passe oublié

    @Transactional
    public void forgotPassword(String email) {
        users.findByEmail(email)
                .filter(u -> u.getStatus() != UserStatus.DISABLED)
                .ifPresent(user -> {
                    Instant now = clock.instant();
                    resetTokens.invalidateAll(user, now);
                    String token = SecureTokens.generate();
                    resetTokens.save(new PasswordResetToken(user, SecureTokens.hash(token),
                            now.plus(props.passwordResetTtl())));
                    mailer.sendPasswordReset(user, token);
                });
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        Instant now = clock.instant();
        var stored = resetTokens.findByTokenHash(SecureTokens.hash(token))
                .filter(t -> t.isUsable(now))
                .orElseThrow(() -> ApiException.badRequest("Ce lien de réinitialisation est invalide ou a expiré."));
        stored.markUsed(now);
        User user = stored.getUser();
        user.changePassword(passwordEncoder.encode(newPassword));
        refreshTokens.revokeAllForUser(user, now);
    }

    public record Session(User user, AccessTokenService.IssuedToken accessToken, String refreshToken,
            UUID refreshTokenId) {
    }
}
