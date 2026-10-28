package dj.redseabunkering.crm;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

/** Vérifie le socle : migrations Flyway, santé, routes publiques et protection par défaut. */
class CrmApplicationTests extends IntegrationTest {

    @Autowired
    TestRestTemplate http;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void migrationsCreateReferenceData() {
        assertThat(jdbc.queryForObject("select count(*) from pipeline_stages", Integer.class)).isEqualTo(7);
        assertThat(jdbc.queryForObject("select reference_price from fuel_grades where code = 'VLSFO'", Integer.class))
                .isEqualTo(612);
        assertThat(jdbc.queryForObject("select count(*) from delivery_locations", Integer.class)).isEqualTo(3);
    }

    @Test
    void healthIsUp() {
        var res = http.getForEntity("/actuator/health", String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).contains("UP");
    }

    @Test
    void publicInfoIsOpen() {
        var res = http.getForEntity("/api/public/info", String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).contains("Red Sea Bunkering CRM");
    }

    @Test
    void privateApiRequiresAuthentication() {
        var res = http.getForEntity("/api/contacts", String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void dealAmountIsComputedByDatabase() {
        jdbc.update("""
                insert into deals (id, vessel_name, imo, fuel_grade, quantity_mt, price_usd_per_mt, stage)
                values (gen_random_uuid(), 'MV TEST', '9876543', 'VLSFO', 850, 612, 'RECUE')""");
        assertThat(jdbc.queryForObject("select amount_usd from deals where vessel_name = 'MV TEST'", Long.class))
                .isEqualTo(520_200L);
    }
}
