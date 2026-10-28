package dj.redseabunkering.crm.user;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import dj.redseabunkering.crm.common.BaseEntity;

@Entity
@Table(name = "users")
public class User extends BaseEntity {

    /** Nombre d'échecs de connexion consécutifs avant blocage temporaire. */
    public static final int MAX_FAILED_LOGINS = 5;

    @Column(nullable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(nullable = false)
    private String initials;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role = Role.SALES;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus status = UserStatus.PENDING;

    @Column(nullable = false)
    private String language = "fr";

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "failed_logins", nullable = false)
    private int failedLogins;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    protected User() {
    }

    public User(String email, String passwordHash, String firstName, String lastName) {
        this.email = normalizeEmail(email);
        this.passwordHash = passwordHash;
        rename(firstName, lastName);
    }

    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    public void rename(String firstName, String lastName) {
        this.firstName = firstName.trim();
        this.lastName = lastName.trim();
        this.initials = (initial(this.firstName) + initial(this.lastName)).toUpperCase(Locale.ROOT);
    }

    private static String initial(String s) {
        return s.isEmpty() ? "" : s.substring(0, 1);
    }

    public String fullName() {
        return firstName + " " + lastName;
    }

    public boolean isLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    /** Enregistre un échec ; renvoie true si le compte vient d'être bloqué. */
    public boolean registerFailedLogin(Instant now, Duration lockDuration) {
        failedLogins++;
        if (failedLogins >= MAX_FAILED_LOGINS) {
            failedLogins = 0;
            lockedUntil = now.plus(lockDuration);
            return true;
        }
        return false;
    }

    public void registerSuccessfulLogin(Instant now) {
        failedLogins = 0;
        lockedUntil = null;
        lastLoginAt = now;
    }

    public void markEmailVerified(Instant now, boolean requiresApproval) {
        emailVerifiedAt = now;
        if (status == UserStatus.PENDING) {
            status = requiresApproval ? UserStatus.PENDING_APPROVAL : UserStatus.ACTIVE;
        }
    }

    public void changePassword(String passwordHash) {
        this.passwordHash = passwordHash;
        this.failedLogins = 0;
        this.lockedUntil = null;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getInitials() {
        return initials;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public UserStatus getStatus() {
        return status;
    }

    public void setStatus(UserStatus status) {
        this.status = status;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public Instant getEmailVerifiedAt() {
        return emailVerifiedAt;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public int getFailedLogins() {
        return failedLogins;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }
}
