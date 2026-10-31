package dj.redseabunkering.crm.activity;

import java.time.Instant;
import java.util.UUID;

public record ActivityDto(UUID id, ActivityType type, String text, Instant occurredAt, UUID authorId,
        UUID dealId, String dealVessel, UUID contactId, String contactName, UUID companyId) {
}
