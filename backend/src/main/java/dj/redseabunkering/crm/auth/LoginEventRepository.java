package dj.redseabunkering.crm.auth;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LoginEventRepository extends JpaRepository<LoginEvent, UUID> {
}
