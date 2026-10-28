package dj.redseabunkering.crm.auth;

import java.time.Instant;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import dj.redseabunkering.crm.common.StrongPassword;
import dj.redseabunkering.crm.user.UserDto;

/** Corps des requêtes et réponses du parcours d'authentification. */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank(message = "Le prénom est obligatoire.") @Size(max = 80, message = "80 caractères maximum.")
            String firstName,
            @NotBlank(message = "Le nom est obligatoire.") @Size(max = 80, message = "80 caractères maximum.")
            String lastName,
            @NotBlank(message = "L'e-mail est obligatoire.") @Email(message = "Adresse e-mail invalide.")
            @Size(max = 254, message = "Adresse e-mail trop longue.")
            String email,
            @StrongPassword
            String password) {
    }

    public record LoginRequest(
            @NotBlank(message = "L'e-mail est obligatoire.") @Size(max = 254) String email,
            @NotBlank(message = "Le mot de passe est obligatoire.") @Size(max = 128) String password) {
    }

    public record TokenRequest(@NotBlank(message = "Lien invalide.") @Size(max = 100) String token) {
    }

    public record ForgotPasswordRequest(
            @NotBlank(message = "L'e-mail est obligatoire.") @Email(message = "Adresse e-mail invalide.")
            @Size(max = 254) String email) {
    }

    public record ResetPasswordRequest(
            @NotBlank(message = "Lien invalide.") @Size(max = 100) String token,
            @StrongPassword String password) {
    }

    public record AuthResponse(String accessToken, Instant expiresAt, UserDto user) {
    }

    public record MessageResponse(String message) {
    }
}
