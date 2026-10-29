package dj.redseabunkering.crm.audit;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Écrit le journal d'audit dans la transaction de l'opération auditée (tout ou rien). */
@Service
public class AuditService {

    private final AuditLogRepository log;
    private final Clock clock;

    public AuditService(AuditLogRepository log, Clock clock) {
        this.log = log;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(UUID actorId, String action, String entityType, UUID entityId, Map<String, Object> details) {
        log.save(new AuditLog(actorId, action, entityType, entityId, details, clock.instant()));
    }
}
