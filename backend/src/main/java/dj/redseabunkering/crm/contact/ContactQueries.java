package dj.redseabunkering.crm.contact;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import dj.redseabunkering.crm.common.PageResponse;
import dj.redseabunkering.crm.contact.ContactDtos.ContactList;
import dj.redseabunkering.crm.contact.ContactDtos.ContactRow;
import dj.redseabunkering.crm.contact.ContactScoring.Score;

/**
 * Lectures des contacts avec leurs agrégats (activités, transactions ouvertes, dernière activité).
 * Le score est calculé en Java par {@link ContactScoring} : une seule source de vérité pour la liste et la fiche.
 */
@Component
public class ContactQueries {

    private static final String SELECT = """
            select c.id, c.first_name, c.last_name, c.title, c.email, c.phone, c.company_id, co.name as company_name,
                   c.lifecycle, c.source, c.owner_id, c.created_at,
                   coalesce(act.n, 0) as activity_count, act.last_at,
                   coalesce(od.amount, 0) as open_amount
            from contacts c
            left join companies co on co.id = c.company_id
            left join (select contact_id, count(*) as n, max(occurred_at) as last_at
                       from activities where contact_id is not null group by contact_id) act on act.contact_id = c.id
            left join (select d.contact_id, sum(d.amount_usd) as amount
                       from deals d join pipeline_stages s on s.code = d.stage
                       where not s.is_won and not s.is_lost and d.contact_id is not null
                       group by d.contact_id) od on od.contact_id = c.id
            """;

    private static final Map<String, Comparator<Scored>> SORTS = Map.of(
            "name", Comparator.comparing((Scored s) -> (s.row.lastName() + " " + s.row.firstName()).toLowerCase()),
            "company", Comparator.comparing((Scored s) -> s.row.companyName() == null ? "~" : s.row.companyName().toLowerCase()),
            "lifecycle", Comparator.comparing((Scored s) -> s.row.lifecycle()),
            "score", Comparator.comparingInt((Scored s) -> s.row.score()),
            "lastActivity", Comparator.comparing((Scored s) -> s.row.lastActivityAt()));

    private final NamedParameterJdbcTemplate jdbc;

    public ContactQueries(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Liste filtrée et triée. Le tri par score impose de scorer tous les contacts filtrés : volume raisonnable
     * pour un CRM interne (quelques milliers de lignes), à revoir si la base grossit fortement.
     */
    public ContactList list(String q, UUID ownerId, UUID companyId, Lifecycle lifecycle, String sort, boolean desc,
            int page, int size) {
        List<Scored> all = fetch(q, ownerId, companyId);
        Map<Lifecycle, Long> counts = new EnumMap<>(Lifecycle.class);
        for (Lifecycle l : Lifecycle.values()) {
            counts.put(l, 0L);
        }
        all.forEach(s -> counts.merge(s.row.lifecycle(), 1L, Long::sum));
        var p = PageResponse.of(sortAndFilter(all, lifecycle, sort, desc), page, size);
        return new ContactList(p.content(), p.page(), p.size(), p.totalElements(), p.totalPages(), counts, all.size());
    }

    /** Tous les contacts filtrés, triés par nom, pour l'export CSV (sans pagination). */
    public List<ContactRow> export(String q, UUID ownerId, Lifecycle lifecycle) {
        return sortAndFilter(fetch(q, ownerId, null), lifecycle, "name", false);
    }

    private List<Scored> fetch(String q, UUID ownerId, UUID companyId) {
        var params = new MapSqlParameterSource();
        var where = new StringBuilder(" where true");
        if (q != null && !q.isBlank()) {
            where.append(" and (c.first_name || ' ' || c.last_name || ' ' || coalesce(c.email, '') || ' '"
                    + " || coalesce(co.name, '') || ' ' || coalesce(c.phone, '')) ilike :q");
            params.addValue("q", "%" + escapeLike(q.trim()) + "%");
        }
        if (ownerId != null) {
            where.append(" and c.owner_id = :owner");
            params.addValue("owner", ownerId);
        }
        if (companyId != null) {
            where.append(" and c.company_id = :company");
            params.addValue("company", companyId);
        }
        return jdbc.query(SELECT + where, params, (rs, i) -> scored(rs));
    }

    private static List<ContactRow> sortAndFilter(List<Scored> all, Lifecycle lifecycle, String sort, boolean desc) {
        Comparator<Scored> order = SORTS.getOrDefault(sort, SORTS.get("score"));
        if (desc) {
            order = order.reversed();
        }
        return all.stream()
                .filter(s -> lifecycle == null || s.row.lifecycle() == lifecycle)
                .sorted(order.thenComparing(s -> s.row.id()))
                .map(s -> s.row)
                .toList();
    }

    public Optional<ScoredContact> find(UUID id) {
        return jdbc.query(SELECT + " where c.id = :id", Map.of("id", id), (rs, i) -> scored(rs)).stream()
                .findFirst()
                .map(s -> new ScoredContact(s.row, s.score));
    }

    private static Scored scored(ResultSet rs) throws SQLException {
        Lifecycle lifecycle = Lifecycle.valueOf(rs.getString("lifecycle"));
        BigDecimal openAmount = rs.getBigDecimal("open_amount");
        Score score = ContactScoring.score(rs.getString("source"), lifecycle, rs.getString("title"),
                rs.getLong("activity_count"), openAmount);
        Instant created = rs.getTimestamp("created_at").toInstant();
        Timestamp last = rs.getTimestamp("last_at");
        var row = new ContactRow(rs.getObject("id", UUID.class), rs.getString("first_name"), rs.getString("last_name"),
                rs.getString("title"), rs.getString("email"), rs.getString("phone"),
                rs.getObject("company_id", UUID.class), rs.getString("company_name"), lifecycle,
                rs.getString("source"), rs.getObject("owner_id", UUID.class), score.total(),
                last != null ? last.toInstant() : created, created);
        return new Scored(row, score);
    }

    static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private record Scored(ContactRow row, Score score) {
    }

    public record ScoredContact(ContactRow row, Score score) {
    }
}
