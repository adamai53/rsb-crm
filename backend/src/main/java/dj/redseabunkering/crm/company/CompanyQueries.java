package dj.redseabunkering.crm.company;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import dj.redseabunkering.crm.common.PageResponse;
import dj.redseabunkering.crm.company.CompanyDtos.CompanyRow;
import dj.redseabunkering.crm.company.CompanyDtos.ContactMini;

/** Lectures agrégées des entreprises (contacts, transactions ouvertes, volume et CA livrés). */
@Component
public class CompanyQueries {

    private static final String SELECT = """
            select c.id, c.name, c.type, c.country, c.city, c.fleet_size, c.website, c.owner_id, c.created_at,
                   (select count(*) from contacts x where x.company_id = c.id) as contacts_count,
                   coalesce(agg.open_count, 0) as open_count,
                   coalesce(agg.delivered_mt, 0) as delivered_mt,
                   coalesce(agg.revenue, 0) as revenue
            from companies c
            left join (
                select d.company_id,
                       count(*) filter (where not s.is_won and not s.is_lost) as open_count,
                       sum(d.quantity_mt) filter (where s.is_won) as delivered_mt,
                       sum(d.amount_usd) filter (where s.is_won) as revenue
                from deals d join pipeline_stages s on s.code = d.stage
                group by d.company_id
            ) agg on agg.company_id = c.id
            """;

    /** Tris autorisés (liste blanche : aucune entrée utilisateur dans le SQL). */
    private static final Map<String, String> SORTS = Map.of(
            "revenue", "revenue desc, c.name",
            "name", "lower(c.name)",
            "fleet", "c.fleet_size desc nulls last, c.name",
            "openDeals", "open_count desc, c.name",
            "createdAt", "c.created_at desc");

    private final NamedParameterJdbcTemplate jdbc;

    public CompanyQueries(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public PageResponse<CompanyRow> list(String q, UUID ownerId, String sort, int page, int size) {
        var params = new MapSqlParameterSource();
        var where = new StringBuilder(" where true");
        if (q != null && !q.isBlank()) {
            where.append(" and (c.name || ' ' || coalesce(c.country, '') || ' ' || coalesce(c.city, '') || ' ' || c.type)"
                    + " ilike :q");
            params.addValue("q", "%" + escapeLike(q.trim()) + "%");
        }
        if (ownerId != null) {
            where.append(" and c.owner_id = :owner");
            params.addValue("owner", ownerId);
        }
        long total = jdbc.queryForObject("select count(*) from companies c" + where, params, Long.class);
        int safeSize = Math.max(1, Math.min(size, 200));
        int safePage = Math.max(0, page);
        params.addValue("limit", safeSize).addValue("offset", (long) safePage * safeSize);
        String order = SORTS.getOrDefault(sort, SORTS.get("revenue"));
        List<CompanyRow> rows = jdbc.query(SELECT + where + " order by " + order + " limit :limit offset :offset",
                params, (rs, i) -> row(rs));
        return new PageResponse<>(rows, safePage, safeSize, total, (int) Math.ceil(total / (double) safeSize));
    }

    public Optional<CompanyRow> find(UUID id) {
        return jdbc.query(SELECT + " where c.id = :id", Map.of("id", id), (rs, i) -> row(rs)).stream().findFirst();
    }

    public List<ContactMini> contacts(UUID companyId) {
        return jdbc.query("""
                select id, first_name, last_name, title, lifecycle from contacts
                where company_id = :id order by last_name, first_name""", Map.of("id", companyId),
                (rs, i) -> new ContactMini(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getString(5)));
    }

    static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static CompanyRow row(ResultSet rs) throws SQLException {
        Timestamp created = rs.getTimestamp("created_at");
        Integer fleet = rs.getObject("fleet_size", Integer.class);
        return new CompanyRow(rs.getObject("id", UUID.class), rs.getString("name"),
                CompanyType.valueOf(rs.getString("type")), rs.getString("country"), rs.getString("city"),
                fleet, rs.getString("website"), rs.getObject("owner_id", UUID.class),
                rs.getLong("contacts_count"), rs.getLong("open_count"), rs.getBigDecimal("delivered_mt"),
                rs.getBigDecimal("revenue"), created.toInstant());
    }
}
