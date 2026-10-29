package dj.redseabunkering.crm.deal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PipelineStageRepository extends JpaRepository<PipelineStage, UUID> {

    Optional<PipelineStage> findByCode(String code);

    List<PipelineStage> findAllByOrderBySortOrder();
}
