package dj.redseabunkering.crm.quote;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import dj.redseabunkering.crm.quote.QuoteDtos.QuoteRow;

/** Lectures des devis avec la transaction, le client et les montants calculés. */
@Component
public class QuoteQueries {

    private static final String SELECT = """
            select q.id, q.number, q.deal_id, d.vessel_name, d.imo, d.company_id, co.name as company_name,
                   d.contact_id, c.first_name || ' ' || c.last_name as contact_name, c.email as contact_email,
                   d.fuel_grade, g.label as grade_label, d.quantity_mt, q.price_usd_per_mt, q.barge_fee_usd,
                   q.status, q.valid_until, q.sent_at, q.created_at, d.eta, dl.label as location_label, d.owner_id,
                   u.first_name || ' ' || u.last_name as owner_name
            from quotes q
            join deals d on d.id = q.deal_id
            join fuel_grades g on g.code = d.fuel_grade
            left join companies co on co.id = d.company_id
            left join contacts c on c.id = d.contact_id
            left join delivery_locations dl on dl.code = d.delivery_location
            left join users u on u.id = d.owner_id
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public QuoteQueries(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<QuoteRow> list(UUID dealId) {
        var params = new MapSqlParameterSource();
        String where = "";
        if (dealId != null) {
            where = " where q.deal_id = :deal";
            params.addValue("deal", dealId);
        }
        return jdbc.query(SELECT + where + " order by q.created_at desc, q.number desc limit 500", params,
                (rs, i) -> row(rs));
    }

    public Optional<QuoteRow> find(UUID id) {
        return jdbc.query(SELECT + " where q.id = :id", Map.of("id", id), (rs, i) -> row(rs)).stream().findFirst();
    }

    private static QuoteRow row(ResultSet rs) throws SQLException {
        BigDecimal quantity = rs.getBigDecimal("quantity_mt");
        BigDecimal price = rs.getBigDecimal("price_usd_per_mt");
        BigDecimal barge = rs.getBigDecimal("barge_fee_usd");
        BigDecimal product = quantity.multiply(price);
        Timestamp sent = rs.getTimestamp("sent_at");
        return new QuoteRow(rs.getObject("id", UUID.class), rs.getString("number"),
                rs.getObject("deal_id", UUID.class), rs.getString("vessel_name"), rs.getString("imo"),
                rs.getObject("company_id", UUID.class), rs.getString("company_name"),
                rs.getObject("contact_id", UUID.class), rs.getString("contact_name"), rs.getString("contact_email"),
                rs.getString("fuel_grade"), rs.getString("grade_label"), quantity, price, barge, product,
                product.add(barge), QuoteStatus.valueOf(rs.getString("status")),
                rs.getTimestamp("valid_until").toInstant(), sent == null ? null : sent.toInstant(),
                rs.getTimestamp("created_at").toInstant(), rs.getObject("eta", LocalDate.class),
                rs.getString("location_label"), rs.getObject("owner_id", UUID.class), rs.getString("owner_name"));
    }
}
