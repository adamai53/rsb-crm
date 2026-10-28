package dj.redseabunkering.crm.auth;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import dj.redseabunkering.crm.user.User;

@Entity
@Table(name = "password_reset_tokens")
public class PasswordResetToken extends OneTimeToken {

    protected PasswordResetToken() {
    }

    public PasswordResetToken(User user, String tokenHash, Instant expiresAt) {
        super(user, tokenHash, expiresAt);
    }
}
