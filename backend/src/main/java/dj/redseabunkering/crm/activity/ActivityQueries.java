package dj.redseabunkering.crm.activity;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/** Lectures du journal d'activité, avec le navire et le contact liés. */
@Component
public class ActivityQueries {

    private static final String SELECT = """
            select a.id, a.type, a.text, a.occurred_at, a.author_id, a.deal_id, d.vessel_name,
                   a.contact_id, c.first_name || ' ' || c.last_name as contact_name, a.company_id
            from activities a
            left join deals d on d.id = a.deal_id
            left join contacts c on c.id = a.contact_id
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public ActivityQueries(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** {@code column} est une constante de l'appelant, jamais une saisie. */
    List<ActivityDto> forRecord(String column, UUID id, int limit) {
        return jdbc.query(SELECT + " where " + column + " = :id order by a.occurred_at desc limit :limit",
                Map.of("id", id, "limit", limit), ActivityQueries::row);
    }

    /** Dernières notes, appels, e-mails et réunions de l'équipe (sans les mises à jour automatiques). */
    public List<ActivityDto> recent(int limit) {
        return jdbc.query(SELECT + " where a.type <> 'SYSTEM' order by a.occurred_at desc limit :limit",
                Map.of("limit", limit), ActivityQueries::row);
    }

    private static ActivityDto row(ResultSet rs, int i) throws SQLException {
        return new ActivityDto(rs.getObject(1, UUID.class), ActivityType.valueOf(rs.getString(2)),
                rs.getString(3), rs.getTimestamp(4).toInstant(), rs.getObject(5, UUID.class),
                rs.getObject(6, UUID.class), rs.getString(7), rs.getObject(8, UUID.class),
                rs.getString(9), rs.getObject(10, UUID.class));
    }
}
