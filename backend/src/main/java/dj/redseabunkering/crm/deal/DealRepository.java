package dj.redseabunkering.crm.deal;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DealRepository extends JpaRepository<Deal, UUID> {
}
