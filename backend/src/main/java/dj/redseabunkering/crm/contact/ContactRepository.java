package dj.redseabunkering.crm.contact;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ContactRepository extends JpaRepository<Contact, UUID> {

    @Query("select count(c) > 0 from Contact c where lower(c.email) = lower(:email)")
    boolean existsByEmailIgnoreCase(String email);
}
