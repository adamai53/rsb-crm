package dj.redseabunkering.crm.auth;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import dj.redseabunkering.crm.user.User;

@Entity
@Table(name = "email_verification_tokens")
public class EmailVerificationToken extends OneTimeToken {

    protected EmailVerificationToken() {
    }

    public EmailVerificationToken(User user, String tokenHash, Instant expiresAt) {
        super(user, tokenHash, expiresAt);
    }
}
