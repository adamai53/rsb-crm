package dj.redseabunkering.crm.deal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Transactions et tâches rattachées à une fiche (contact ou entreprise), en lecture seule. */
@Component
public class RelatedRecords {

    private static final String DEALS = """
            select d.id, d.vessel_name, d.fuel_grade, d.quantity_mt, d.amount_usd, d.eta, d.stage, s.label,
                   s.is_won, s.is_lost
            from deals d join pipeline_stages s on s.code = d.stage
            where d.%s = ?
            order by (s.is_won or s.is_lost), d.eta nulls last, d.created_at desc""";

    private static final String TASKS = """
            select id, title, due_date, priority, done, owner_id from tasks
            where %s = ? order by done, due_date""";

    private final JdbcTemplate jdbc;

    public RelatedRecords(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<DealSummary> dealsOfContact(UUID contactId) {
        return deals("contact_id", contactId);
    }

    public List<DealSummary> dealsOfCompany(UUID companyId) {
        return deals("company_id", companyId);
    }

    public List<TaskSummary> tasksOfContact(UUID contactId) {
        return jdbc.query(TASKS.formatted("contact_id"), (rs, i) -> new TaskSummary(rs.getObject(1, UUID.class),
                rs.getString(2), rs.getObject(3, LocalDate.class), rs.getString(4), rs.getBoolean(5),
                rs.getObject(6, UUID.class)), contactId);
    }

    private List<DealSummary> deals(String column, UUID id) {
        return jdbc.query(DEALS.formatted(column), (rs, i) -> new DealSummary(rs.getObject(1, UUID.class),
                rs.getString(2), rs.getString(3), rs.getBigDecimal(4), rs.getBigDecimal(5),
                rs.getObject(6, LocalDate.class), rs.getString(7), rs.getString(8), rs.getBoolean(9),
                rs.getBoolean(10)), id);
    }

    public record DealSummary(UUID id, String vesselName, String fuelGrade, BigDecimal quantityMt,
            BigDecimal amountUsd, LocalDate eta, String stage, String stageLabel, boolean won, boolean lost) {
    }

    public record TaskSummary(UUID id, String title, LocalDate dueDate, String priority, boolean done,
            UUID ownerId) {
    }
}
