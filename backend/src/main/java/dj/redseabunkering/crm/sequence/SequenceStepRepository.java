package dj.redseabunkering.crm.sequence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SequenceStepRepository extends JpaRepository<SequenceStep, UUID> {

    List<SequenceStep> findBySequenceIdOrderByDayOffset(UUID sequenceId);
}
