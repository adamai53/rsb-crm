package dj.redseabunkering.crm.sequence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SequenceRepository extends JpaRepository<Sequence, UUID> {

    List<Sequence> findByActiveTrueOrderByName();
}
