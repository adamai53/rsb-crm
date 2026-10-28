package dj.redseabunkering.crm.user;

import java.time.Clock;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import dj.redseabunkering.crm.auth.AuthService;
import dj.redseabunkering.crm.auth.RefreshCookie;
import dj.redseabunkering.crm.auth.RefreshTokenRepository;
import dj.redseabunkering.crm.common.ApiException;
import dj.redseabunkering.crm.common.StrongPassword;

/**
 * Profil de l'utilisateur connecté : nom et langue sur /api/users/me ; le mot de passe sur /api/auth/password,
 * pour recevoir le cookie de refresh (limité à /api/auth) et conserver la session courante.
 */
@RestController
public class ProfileController {

    private final UserRepository users;
    private final UserMapper mapper;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenRepository refreshTokens;
    private final RefreshCookie cookie;
    private final AuthService auth;
    private final Clock clock;

    public ProfileController(UserRepository users, UserMapper mapper, PasswordEncoder passwordEncoder,
            RefreshTokenRepository refreshTokens, RefreshCookie cookie, AuthService auth, Clock clock) {
        this.users = users;
        this.mapper = mapper;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokens = refreshTokens;
        this.cookie = cookie;
        this.auth = auth;
        this.clock = clock;
    }

    @PatchMapping("/api/users/me")
    @Transactional
    public UserDto update(@Valid @RequestBody ProfileRequest req, @AuthenticationPrincipal Jwt jwt) {
        User user = current(jwt);
        user.rename(req.firstName(), req.lastName());
        user.setLanguage(req.language());
        return mapper.toDto(user);
    }

    /** Change le mot de passe et ferme les autres sessions (la session courante est conservée). */
    @PostMapping("/api/auth/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void changePassword(@Valid @RequestBody PasswordRequest req, @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest http) {
        User user = current(jwt);
        if (!passwordEncoder.matches(req.currentPassword(), user.getPasswordHash())) {
            throw ApiException.badRequest("Le mot de passe actuel est incorrect.");
        }
        user.changePassword(passwordEncoder.encode(req.newPassword()));
        UUID keep = cookie.read(http).map(auth::sessionFamily).orElse(null);
        if (keep == null) {
            refreshTokens.revokeAllForUser(user, clock.instant());
        } else {
            refreshTokens.revokeAllForUserExcept(user, keep, clock.instant());
        }
    }

    private User current(Jwt jwt) {
        return users.findById(UUID.fromString(jwt.getSubject()))
                .orElseThrow(() -> ApiException.unauthorized("Session expirée. Veuillez vous reconnecter."));
    }

    public record ProfileRequest(
            @NotBlank(message = "Le prénom est obligatoire.") @Size(max = 80, message = "80 caractères maximum.")
            String firstName,
            @NotBlank(message = "Le nom est obligatoire.") @Size(max = 80, message = "80 caractères maximum.")
            String lastName,
            @NotBlank @Pattern(regexp = "fr|en", message = "Langue non prise en charge.") String language) {
    }

    public record PasswordRequest(
            @NotBlank(message = "Le mot de passe actuel est obligatoire.") @Size(max = 128) String currentPassword,
            @StrongPassword String newPassword) {
    }
}
