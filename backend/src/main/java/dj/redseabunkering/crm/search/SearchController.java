package dj.redseabunkering.crm.search;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Recherche globale (Ctrl+K) : contacts, entreprises, navires et IMO, dès 2 caractères. */
@RestController
@RequestMapping("/api/search")
public class SearchController {

    private static final int LIMIT = 10;

    /** Contacts, puis entreprises, puis transactions ; à l'intérieur, les débuts de mot d'abord. */
    private static final String SQL = """
            select * from (
                select 'contact' as type, c.id, c.first_name || ' ' || c.last_name as label,
                       'Contact' || coalesce(' · ' || co.name, '') as sub, 1 as kind,
                       (c.first_name || ' ' || c.last_name) ilike :prefix as starts
                from contacts c left join companies co on co.id = c.company_id
                where (c.first_name || ' ' || c.last_name || ' ' || coalesce(c.email, '')) ilike :q
                union all
                select 'company', co.id, co.name, 'Entreprise' || coalesce(' · ' || co.country, ''), 2,
                       co.name ilike :prefix
                from companies co where co.name ilike :q
                union all
                select 'deal', d.id, d.vessel_name, 'Transaction · ' || coalesce('IMO ' || d.imo || ' · ', '') || s.label, 3,
                       d.vessel_name ilike :prefix
                from deals d join pipeline_stages s on s.code = d.stage
                where (d.vessel_name || ' ' || coalesce(d.imo, '')) ilike :q
            ) r
            order by kind, starts desc, label
            limit :limit
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public SearchController(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    public List<SearchResult> search(@RequestParam String q) {
        String term = q == null ? "" : q.trim();
        if (term.length() < 2) {
            return List.of();
        }
        if (term.length() > 100) {
            term = term.substring(0, 100);
        }
        String escaped = term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return jdbc.query(SQL, Map.of("q", "%" + escaped + "%", "prefix", escaped + "%", "limit", LIMIT),
                (rs, i) -> new SearchResult(rs.getString("type"), rs.getObject("id", UUID.class),
                        rs.getString("label"), rs.getString("sub")));
    }

    public record SearchResult(String type, UUID id, String label, String sub) {
    }
}
