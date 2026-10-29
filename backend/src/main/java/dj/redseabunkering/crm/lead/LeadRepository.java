package dj.redseabunkering.crm.lead;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LeadRepository extends JpaRepository<Lead, UUID> {

    List<Lead> findAllByOrderByReceivedAtDesc();

    List<Lead> findByStatusOrderByReceivedAtDesc(LeadStatus status);

    long countByStatus(LeadStatus status);
}
