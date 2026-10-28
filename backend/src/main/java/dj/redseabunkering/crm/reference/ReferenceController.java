package dj.redseabunkering.crm.reference;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dj.redseabunkering.crm.common.Labeled;
import dj.redseabunkering.crm.company.CompanyType;
import dj.redseabunkering.crm.contact.Lifecycle;
import dj.redseabunkering.crm.contact.Sources;

/** Listes de valeurs pour les formulaires et filtres, chargées une fois par le frontend. */
@RestController
@RequestMapping("/api/reference")
public class ReferenceController {

    private final JdbcTemplate jdbc;

    public ReferenceController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    public ReferenceData all() {
        return new ReferenceData(
                options(CompanyType.values()),
                options(Lifecycle.values()),
                Sources.ALL,
                jdbc.query("""
                        select code, label, probability, is_won, is_lost from pipeline_stages order by sort_order""",
                        (rs, i) -> new Stage(rs.getString(1), rs.getString(2), rs.getInt(3), rs.getBoolean(4),
                                rs.getBoolean(5))),
                jdbc.query("""
                        select code, label, reference_price from fuel_grades where active order by sort_order""",
                        (rs, i) -> new FuelGrade(rs.getString(1), rs.getString(2), rs.getBigDecimal(3))),
                jdbc.query("select code, label from delivery_locations where active order by sort_order",
                        (rs, i) -> new Option(rs.getString(1), rs.getString(2))),
                jdbc.query("""
                        select id, first_name, last_name, initials, role, status = 'ACTIVE'
                        from users where status in ('ACTIVE', 'DISABLED')
                        order by first_name, last_name""",
                        (rs, i) -> new UserRef(rs.getObject(1, UUID.class),
                                rs.getString(2) + " " + rs.getString(3), rs.getString(4), rs.getString(5),
                                rs.getBoolean(6))));
    }

    private static List<Option> options(Labeled[] values) {
        return Arrays.stream(values).map(v -> new Option(v.name(), v.label())).toList();
    }

    public record ReferenceData(List<Option> companyTypes, List<Option> lifecycles, List<String> sources,
            List<Stage> stages, List<FuelGrade> fuelGrades, List<Option> deliveryLocations, List<UserRef> users) {
    }

    public record Option(String code, String label) {
    }

    public record Stage(String code, String label, int probability, boolean won, boolean lost) {
    }

    public record FuelGrade(String code, String label, BigDecimal referencePrice) {
    }

    /** Utilisateur affichable (propriétaire d'un dossier) ; les inactifs restent visibles sur l'historique. */
    public record UserRef(UUID id, String name, String initials, String role, boolean active) {
    }
}
