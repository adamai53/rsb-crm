package dj.redseabunkering.crm.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import dj.redseabunkering.crm.IntegrationTest;
import dj.redseabunkering.crm.auth.AccessTokenService;
import dj.redseabunkering.crm.user.Role;
import dj.redseabunkering.crm.user.User;
import dj.redseabunkering.crm.user.UserRepository;

/** Chargement fidèle au prototype, dates relatives au jour J et garde-fous de la réinitialisation. */
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.seed-demo=true",
        "app.demo.password=DemoPass2026",
        "app.demo.reference-date=2026-10-04",
})
class DemoDataTests extends IntegrationTest {

    private static final LocalDate REF = LocalDate.of(2026, 10, 4);

    @Autowired
    MockMvc mvc;

    @Autowired
    DemoService demo;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    UserRepository users;

    @Autowired
    AccessTokenService tokens;

    @Autowired
    PasswordEncoder passwords;

    @BeforeEach
    void emptyBase() {
        clear();
    }

    /** La base est partagée avec les autres suites : ni données ni commerciaux de démonstration ne doivent rester. */
    @AfterEach
    void clear() {
        for (String table : List.of("sent_emails", "quotes", "sequence_enrollments", "tasks", "activities", "leads",
                "deals", "contacts", "companies", "campaigns")) {
            jdbc.update("delete from " + table);
        }
        jdbc.update("delete from audit_log where action = 'DEMO_SEEDED'");
        jdbc.update("delete from users where email like '%@rsb.local'");
    }

    private long count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Long.class);
    }

    private void assertPrototypeCounts() {
        assertThat(count("companies")).isEqualTo(12);
        assertThat(count("contacts")).isEqualTo(20);
        assertThat(count("deals")).isEqualTo(22);
        assertThat(count("leads")).isEqualTo(6);
        assertThat(count("tasks")).isEqualTo(9);
        assertThat(count("activities")).isEqualTo(14);
        assertThat(count("quotes")).isEqualTo(3);
        assertThat(count("campaigns")).isEqualTo(6);
        assertThat(count("email_templates")).isEqualTo(5);
    }

    @Test
    void loadsPrototypeDataRelativeToReferenceDate() {
        assertThat(demo.seedIfEmpty()).isTrue();
        assertPrototypeCounts();
        assertThat(jdbc.queryForObject("select eta from deals where vessel_name = 'MV Kestrel Dawn'", LocalDate.class))
                .isEqualTo(REF.plusDays(6));
        assertThat(jdbc.queryForObject("select closed_at from deals where vessel_name = 'MV Kestrel Arrow'", LocalDate.class))
                .isEqualTo(REF.minusDays(6));
        assertThat(jdbc.queryForObject("select price_usd_per_mt from deals where vessel_name = 'MV Indigo Pearl'", Integer.class))
                .isEqualTo(606);
        assertThat(jdbc.queryForObject("select lost_reason from deals where vessel_name = 'MV Bab Al Noor'", String.class))
                .isEqualTo("Prix concurrent inférieur");
        assertThat(jdbc.queryForObject("select email from contacts where last_name = 'Ismaël'", String.class))
                .isEqualTo("h.ismael@hornafrica-agency.example");
        assertThat(jdbc.queryForList("select number from quotes order by number", String.class))
                .containsExactly("RSB-Q-2026-0141", "RSB-Q-2026-0142", "RSB-Q-2026-0143");
        assertThat(jdbc.queryForObject("select last_value from quote_number_sequences where year = 2026", Integer.class))
                .isEqualTo(143);
        assertThat(jdbc.queryForObject("select count(*) from tasks where done", Long.class)).isEqualTo(1);
    }

    @Test
    void createsThreeSalesAccountsWithCommonPassword() {
        demo.seedIfEmpty();
        for (String email : List.of("hassan.doualeh@rsb.local", "fatouma.ali@rsb.local", "ibrahim.omar@rsb.local")) {
            User u = users.findByEmail(email).orElseThrow();
            assertThat(u.getRole()).isEqualTo(Role.SALES);
            assertThat(passwords.matches("DemoPass2026", u.getPasswordHash())).isTrue();
        }
        assertThat(jdbc.queryForObject("select count(*) from deals where owner_id is null", Long.class)).isZero();
    }

    @Test
    void neverSeedsOverExistingData() {
        jdbc.update("insert into companies (id, name, type) values (?, 'Vrai client', 'ARMATEUR')", UUID.randomUUID());
        assertThat(demo.seedIfEmpty()).isFalse();
        assertThat(count("companies")).isEqualTo(1);
        assertThat(count("deals")).isZero();
    }

    @Test
    void resetRestoresDataForAdminOnly() throws Exception {
        demo.seedIfEmpty();
        jdbc.update("delete from deals where vessel_name = 'MV Kestrel Dawn'");
        jdbc.update("insert into companies (id, name, type) values (?, 'Ajoutée', 'ARMATEUR')", UUID.randomUUID());
        User sales = users.findByEmail("hassan.doualeh@rsb.local").orElseThrow();
        mvc.perform(post("/api/admin/demo/reset").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.issue(sales).value()))
                .andExpect(status().isForbidden());
        assertThat(count("deals")).isEqualTo(21);

        mvc.perform(post("/api/admin/demo/reset").header(HttpHeaders.AUTHORIZATION, "Bearer " + admin()))
                .andExpect(status().isNoContent());
        assertPrototypeCounts();
        assertThat(jdbc.queryForObject("select count(*) from companies where name = 'Ajoutée'", Long.class)).isZero();
        // Les mêmes comptes commerciaux servent de nouveau
        assertThat(jdbc.queryForObject("select count(*) from users where email like '%@rsb.local'", Long.class)).isEqualTo(3);
    }

    @Test
    void resetRefusedWhenBaseWasNotSeededByDemo() throws Exception {
        jdbc.update("insert into companies (id, name, type) values (?, 'Vrai client', 'ARMATEUR')", UUID.randomUUID());
        mvc.perform(post("/api/admin/demo/reset").header(HttpHeaders.AUTHORIZATION, "Bearer " + admin()))
                .andExpect(status().isConflict());
        assertThat(count("companies")).isEqualTo(1);
    }

    @Test
    void referenceDateCanFollowToday() {
        LocalDate today = LocalDate.of(2027, 1, 15);
        assertThat(DemoData.reference("today", today)).isEqualTo(today);
        assertThat(DemoData.reference("", today)).isEqualTo(today);
        assertThat(DemoData.reference("2026-10-04", today)).isEqualTo(REF);
    }

    private String admin() {
        return tokens.issue(users.findByEmail("admin@test.rsb").orElseThrow()).value();
    }
}
