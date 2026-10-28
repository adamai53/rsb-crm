package dj.redseabunkering.crm.user;

import java.time.Instant;
import java.util.UUID;

public record UserDto(
        UUID id,
        String email,
        String firstName,
        String lastName,
        String initials,
        Role role,
        UserStatus status,
        String language,
        Instant emailVerifiedAt,
        Instant lastLoginAt,
        Instant createdAt) {
}
