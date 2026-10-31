package dj.redseabunkering.crm.sequence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SequenceEnrollmentRepository extends JpaRepository<SequenceEnrollment, UUID> {
}
