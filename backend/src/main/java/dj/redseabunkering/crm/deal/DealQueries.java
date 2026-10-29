package dj.redseabunkering.crm.deal;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import dj.redseabunkering.crm.deal.DealDtos.DealList;
import dj.redseabunkering.crm.deal.DealDtos.DealRow;
import dj.redseabunkering.crm.deal.DealDtos.Summary;
import dj.redseabunkering.crm.deal.PipelineMetrics.OpenAmount;

/** Lectures des transactions avec entreprise, contact, étape et lieu de livraison. */
@Component
public class DealQueries {

    /** Plafond de la vue pipeline : au-delà, filtrer par commercial ou par texte. */
    static final int MAX_ROWS = 1000;

    private static final String SELECT = """
            select d.id, d.vessel_name, d.imo, d.company_id, co.name as company_name, d.contact_id,
                   c.first_name || ' ' || c.last_name as contact_name, d.fuel_grade, d.quantity_mt,
                   d.price_usd_per_mt, d.amount_usd, d.stage, s.label as stage_label, s.probability,
                   s.is_won, s.is_lost, d.owner_id, d.source, d.eta, d.delivery_location,
                   dl.label as location_label, d.closed_at, d.lost_reason, d.created_at
            from deals d
            join pipeline_stages s on s.code = d.stage
            left join companies co on co.id = d.company_id
            left join contacts c on c.id = d.contact_id
            left join delivery_locations dl on dl.code = d.delivery_location
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public DealQueries(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public DealList list(UUID ownerId, String q, String stage) {
        var params = new MapSqlParameterSource();
        var where = new StringBuilder(" where true");
        if (ownerId != null) {
            where.append(" and d.owner_id = :owner");
            params.addValue("owner", ownerId);
        }
        if (q != null && !q.isBlank()) {
            where.append(" and (d.vessel_name || ' ' || coalesce(d.imo, '') || ' ' || coalesce(co.name, '')"
                    + " || ' ' || coalesce(c.first_name || ' ' || c.last_name, '')) ilike :q");
            params.addValue("q", "%" + q.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%");
        }
        if (stage != null && !stage.isBlank()) {
            where.append(" and d.stage = :stage");
            params.addValue("stage", stage);
        }
        params.addValue("limit", MAX_ROWS);
        List<DealRow> rows = jdbc.query(SELECT + where + " order by s.sort_order, d.eta nulls last, d.created_at limit :limit",
                params, (rs, i) -> row(rs));
        return new DealList(rows, summary(rows));
    }

    public Optional<DealRow> find(UUID id) {
        return jdbc.query(SELECT + " where d.id = :id", Map.of("id", id), (rs, i) -> row(rs)).stream().findFirst();
    }

    /** Escales à venir (calendrier) : transactions non perdues dont l'ETA tombe entre hier et J+days. */
    public List<DealRow> upcomingCalls(LocalDate today, int days) {
        return jdbc.query(SELECT + """
                 where not s.is_lost and d.eta between :from and :to
                 order by d.eta, d.vessel_name""",
                Map.of("from", today.minusDays(1), "to", today.plusDays(days)), (rs, i) -> row(rs));
    }

    static Summary summary(List<DealRow> rows) {
        List<DealRow> open = rows.stream().filter(r -> !r.won() && !r.lost()).toList();
        long won = rows.stream().filter(DealRow::won).count();
        long lost = rows.stream().filter(DealRow::lost).count();
        BigDecimal openAmount = open.stream().map(DealRow::amountUsd).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal weighted = PipelineMetrics.weightedPipeline(
                open.stream().map(r -> new OpenAmount(r.amountUsd(), r.probability())).toList());
        return new Summary(open.size(), openAmount, weighted, won, lost, PipelineMetrics.conversionRate(won, lost));
    }

    private static DealRow row(ResultSet rs) throws SQLException {
        return new DealRow(rs.getObject("id", UUID.class), rs.getString("vessel_name"), rs.getString("imo"),
                rs.getObject("company_id", UUID.class), rs.getString("company_name"),
                rs.getObject("contact_id", UUID.class), rs.getString("contact_name"), rs.getString("fuel_grade"),
                rs.getBigDecimal("quantity_mt"), rs.getBigDecimal("price_usd_per_mt"), rs.getBigDecimal("amount_usd"),
                rs.getString("stage"), rs.getString("stage_label"), rs.getInt("probability"), rs.getBoolean("is_won"),
                rs.getBoolean("is_lost"), rs.getObject("owner_id", UUID.class), rs.getString("source"),
                rs.getObject("eta", LocalDate.class), rs.getString("delivery_location"), rs.getString("location_label"),
                rs.getObject("closed_at", LocalDate.class), rs.getString("lost_reason"),
                rs.getTimestamp("created_at").toInstant());
    }
}
