package dj.redseabunkering.crm.user;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<User, UUID> {

    @Query("select u from User u where lower(u.email) = lower(:email)")
    Optional<User> findByEmail(String email);

    boolean existsByRole(Role role);

    List<User> findByRoleAndStatus(Role role, UserStatus status);

    List<User> findAllByOrderByStatusAscLastNameAscFirstNameAsc();
}
