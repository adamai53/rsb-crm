package dj.redseabunkering.crm.auth;

import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import dj.redseabunkering.crm.auth.AuthDtos.AuthResponse;
import dj.redseabunkering.crm.auth.AuthDtos.ForgotPasswordRequest;
import dj.redseabunkering.crm.auth.AuthDtos.LoginRequest;
import dj.redseabunkering.crm.auth.AuthDtos.MessageResponse;
import dj.redseabunkering.crm.auth.AuthDtos.RegisterRequest;
import dj.redseabunkering.crm.auth.AuthDtos.ResetPasswordRequest;
import dj.redseabunkering.crm.auth.AuthDtos.TokenRequest;
import dj.redseabunkering.crm.common.ApiException;
import dj.redseabunkering.crm.user.UserDto;
import dj.redseabunkering.crm.user.UserMapper;
import dj.redseabunkering.crm.user.UserRepository;
import dj.redseabunkering.crm.user.UserStatus;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;
    private final RefreshCookie cookie;
    private final UserRepository users;
    private final UserMapper mapper;

    public AuthController(AuthService auth, RefreshCookie cookie, UserRepository users, UserMapper mapper) {
        this.auth = auth;
        this.cookie = cookie;
        this.users = users;
        this.mapper = mapper;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public MessageResponse register(@Valid @RequestBody RegisterRequest req) {
        auth.register(req);
        return new MessageResponse("Inscription enregistrée. Un lien de confirmation vient d'être envoyé à "
                + req.email() + ".");
    }

    @PostMapping("/verify-email")
    public MessageResponse verifyEmail(@Valid @RequestBody TokenRequest req) {
        UserStatus status = auth.verifyEmail(req.token());
        return new MessageResponse(status == UserStatus.ACTIVE
                ? "Adresse confirmée. Vous pouvez maintenant vous connecter."
                : "Adresse confirmée. Un administrateur doit encore valider votre compte ; vous serez prévenu par e-mail.");
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest req, HttpServletRequest http) {
        return sessionResponse(auth.login(req, ClientInfo.from(http)));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(HttpServletRequest http) {
        String raw = cookie.read(http).orElseThrow(() -> ApiException.unauthorized(AuthService.INVALID_SESSION));
        try {
            return sessionResponse(auth.refresh(raw, ClientInfo.from(http)));
        } catch (ApiException e) {
            return ResponseEntity.status(e.getStatus())
                    .header(HttpHeaders.SET_COOKIE, cookie.clear().toString())
                    .build();
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest http) {
        cookie.read(http).ifPresent(auth::logout);
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookie.clear().toString()).build();
    }

    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public MessageResponse forgotPassword(@Valid @RequestBody ForgotPasswordRequest req) {
        auth.forgotPassword(req.email());
        return new MessageResponse("Si un compte existe pour cette adresse, un lien de réinitialisation vient d'être envoyé.");
    }

    @PostMapping("/reset-password")
    public MessageResponse resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
        auth.resetPassword(req.token(), req.password());
        return new MessageResponse("Mot de passe modifié. Vous pouvez vous connecter.");
    }

    @GetMapping("/me")
    public UserDto me(@AuthenticationPrincipal Jwt jwt) {
        return users.findById(UUID.fromString(jwt.getSubject()))
                .map(mapper::toDto)
                .orElseThrow(() -> ApiException.unauthorized(AuthService.INVALID_SESSION));
    }

    private ResponseEntity<AuthResponse> sessionResponse(AuthService.Session s) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.create(s.refreshToken()).toString())
                .body(new AuthResponse(s.accessToken().value(), s.accessToken().expiresAt(), mapper.toDto(s.user())));
    }
}
