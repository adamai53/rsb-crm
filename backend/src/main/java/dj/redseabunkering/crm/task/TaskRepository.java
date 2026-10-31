package dj.redseabunkering.crm.task;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskRepository extends JpaRepository<Task, UUID> {

    List<Task> findByEnrollmentIdAndSequenceStepId(UUID enrollmentId, UUID sequenceStepId);
}
