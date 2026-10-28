package dj.redseabunkering.crm.company;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CompanyRepository extends JpaRepository<Company, UUID> {

    @Query("select c from Company c where lower(c.name) = lower(:name)")
    Optional<Company> findByNameIgnoreCase(String name);
}
