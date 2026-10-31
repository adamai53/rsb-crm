package dj.redseabunkering.crm.email;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SentEmailRepository extends JpaRepository<SentEmail, UUID> {

    Optional<SentEmail> findByTrackingId(UUID trackingId);
}
