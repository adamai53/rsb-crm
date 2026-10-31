package dj.redseabunkering.crm.quote;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface QuoteRepository extends JpaRepository<Quote, UUID> {

    /** Les devis envoyés dont la validité est dépassée passent en EXPIRE. */
    @Modifying
    @Query("update Quote q set q.status = dj.redseabunkering.crm.quote.QuoteStatus.EXPIRE "
            + "where q.status = dj.redseabunkering.crm.quote.QuoteStatus.ENVOYE and q.validUntil < :now")
    int expireSent(Instant now);
}
