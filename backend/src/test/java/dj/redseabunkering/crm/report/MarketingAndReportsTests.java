package dj.redseabunkering.crm.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import dj.redseabunkering.crm.IntegrationTest;
import dj.redseabunkering.crm.auth.AccessTokenService;
import dj.redseabunkering.crm.campaign.CaptchaVerifier;
import dj.redseabunkering.crm.user.Role;
import dj.redseabunkering.crm.user.User;
import dj.redseabunkering.crm.user.UserRepository;
import dj.redseabunkering.crm.user.UserStatus;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/** Attribution des campagnes, formulaire public, tableau de bord et rapports par périmètre. */
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.public-form.api-key=cle-site",
        "app.public-form.allowed-origins=https://redseabunkering.com",
        "app.public-form.max-per-ip=3",
})
class MarketingAndReportsTests extends IntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    UserRepository users;

    @Autowired
    AccessTokenService tokens;

    @Autowired
    JdbcTemplate jdbc;

    @MockitoBean
    CaptchaVerifier captcha;

    private final LocalDate today = LocalDate.now(ZoneId.of("Africa/Djibouti"));
    private User sales;
    private User otherSales;
    private User manager;
    private String ip;

    @BeforeEach
    void setUp() {
        // Base partagée avec les autres suites : chaque test repart de chiffres connus
        jdbc.update("delete from campaigns");
        jdbc.update("delete from leads");
        jdbc.update("delete from sent_emails");
        jdbc.update("delete from quotes");
        jdbc.update("delete from tasks");
        jdbc.update("delete from activities");
        jdbc.update("delete from deals");
        jdbc.update("delete from contacts");
        sales = user(Role.SALES);
        otherSales = user(Role.SALES);
        manager = user(Role.MANAGER);
        ip = "10.0." + (int) (Math.random() * 250) + "." + (int) (Math.random() * 250);
        when(captcha.verify(any(), any())).thenReturn(true);
    }

    // ------------------------------------------------------------------ campagnes

    @Test
    void campaignMetricsAreComputedFromSources() throws Exception {
        campaign(manager, "Recherche", "Google Ads", 1000, today.minusDays(30), today).andExpect(status().isCreated());
        // Dans la période : 3 leads dont 1 rejeté, 1 transaction directe livrée, 1 livrée issue d'un lead converti
        lead("Google Ads", "NOUVEAU", null);
        lead("Google Ads", "REJETE", null);
        UUID fromLead = deal(sales, "LIVREE", "Google Ads", 100, 500, today);
        lead("Google Ads", "CONVERTI", fromLead);
        deal(sales, "LIVREE", "Google Ads", 200, 500, today);
        // Hors canal et hors période : ignorés
        lead("LinkedIn", "NOUVEAU", null);
        UUID old = deal(sales, "LIVREE", "Google Ads", 999, 500, today);
        jdbc.update("update deals set created_at = now() - interval '60 days' where id = ?", old);

        JsonNode row = body(call(get("/api/marketing/summary"), sales).andExpect(status().isOk())).get("campaigns").get(0);
        assertThat(row.get("leads").asLong()).isEqualTo(3); // nouveau + converti + transaction directe
        assertThat(row.get("won").asLong()).isEqualTo(2);
        assertThat(row.get("revenueUsd").decimalValue()).isEqualByComparingTo("150000");
        assertThat(row.get("costPerLeadUsd").decimalValue()).isEqualByComparingTo("333.33");
        assertThat(row.get("revenuePerUsd").decimalValue()).isEqualByComparingTo("150");
    }

    @Test
    void summaryWithoutLeadsOrSpendAvoidsDivisionByZero() throws Exception {
        campaign(manager, "Salon", "Salon / événement", 0, null, null).andExpect(status().isCreated());
        JsonNode s = body(call(get("/api/marketing/summary"), manager).andExpect(status().isOk()));
        assertThat(s.get("totals").get("costPerLeadUsd").isNull()).isTrue();
        assertThat(s.get("totals").get("revenuePerUsd").isNull()).isTrue();
        assertThat(s.get("totals").get("leadToClientPct").asInt()).isZero();
        assertThat(s.get("channels")).hasSize(1);
    }

    @Test
    void overlappingCampaignsOnSameChannelAreRefused() throws Exception {
        UUID id = UUID.fromString(body(campaign(manager, "A", "LinkedIn", 10, today.minusDays(10), today)
                .andExpect(status().isCreated())).get("id").asText());
        campaign(manager, "B", "LinkedIn", 10, today, today.plusDays(5)).andExpect(status().isConflict());
        campaign(manager, "Sans période", "LinkedIn", 10, null, null).andExpect(status().isConflict());
        campaign(manager, "Autre canal", "Meta Ads", 10, today, today).andExpect(status().isCreated());
        campaign(manager, "Après", "LinkedIn", 10, today.plusDays(1), null).andExpect(status().isCreated());
        // Une campagne ne se chevauche pas elle-même
        call(put("/api/campaigns/" + id).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(
                campaignBody("A bis", "LinkedIn", 20, today.minusDays(9), today))), manager)
                .andExpect(status().isNoContent());
    }

    @Test
    void invalidCampaignsAreRefused() throws Exception {
        campaign(manager, "X", "Pigeon voyageur", 10, null, null).andExpect(status().isBadRequest());
        campaign(manager, "X", "LinkedIn", -1, null, null).andExpect(status().isBadRequest());
        campaign(manager, "X", "LinkedIn", 1, today, today.minusDays(1)).andExpect(status().isBadRequest());
    }

    @Test
    void onlyManagersEditCampaignsAndSeeFormConfig() throws Exception {
        campaign(sales, "X", "LinkedIn", 10, null, null).andExpect(status().isForbidden());
        UUID id = UUID.fromString(body(campaign(manager, "X", "LinkedIn", 10, null, null)
                .andExpect(status().isCreated())).get("id").asText());
        call(delete("/api/campaigns/" + id), sales).andExpect(status().isForbidden());
        call(get("/api/campaigns"), sales).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
        call(get("/api/marketing/form-config"), sales).andExpect(status().isForbidden());
        call(get("/api/marketing/form-config"), manager).andExpect(status().isOk())
                .andExpect(jsonPath("$.apiKey").value("cle-site"));
        call(delete("/api/campaigns/" + id), manager).andExpect(status().isNoContent());
    }

    // ------------------------------------------------------------------ formulaire public

    @Test
    void publicFormCreatesLeadInInbox() throws Exception {
        submit(form("Google Ads"), "cle-site").andExpect(status().isAccepted());
        Map<String, Object> row = jdbc.queryForMap("select * from leads");
        assertThat(row).containsEntry("status", "NOUVEAU").containsEntry("source", "Google Ads")
                .containsEntry("company_name", "Atlantico Bulk").containsEntry("email", "s.marin@atlantico.example");
    }

    @Test
    void publicFormRejectsBadKeyAndInvalidData() throws Exception {
        submit(form(null), "mauvaise").andExpect(status().isUnauthorized());
        Map<String, Object> bad = form(null);
        bad.put("email", "pas-un-mail");
        bad.put("imo", "12");
        submit(bad, "cle-site").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").exists()).andExpect(jsonPath("$.errors.imo").exists());
        Map<String, Object> grade = form(null);
        grade.put("fuelGrade", "KEROSENE");
        submit(grade, "cle-site").andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("select count(*) from leads", Long.class)).isZero();
    }

    @Test
    void unknownSourceFallsBackToWebsite() throws Exception {
        submit(form("<script>x</script>"), "cle-site").andExpect(status().isAccepted());
        assertThat(jdbc.queryForObject("select source from leads", String.class)).isEqualTo("Site web");
    }

    @Test
    void honeypotAndFailedCaptchaCreateNothing() throws Exception {
        Map<String, Object> robot = form(null);
        robot.put("website", "http://spam.example");
        submit(robot, "cle-site").andExpect(status().isAccepted());
        when(captcha.verify(eq("mauvais"), any())).thenReturn(false);
        Map<String, Object> failed = form(null);
        failed.put("captchaToken", "mauvais");
        submit(failed, "cle-site").andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("select count(*) from leads", Long.class)).isZero();
    }

    @Test
    void publicFormIsRateLimited() throws Exception {
        for (int i = 0; i < 3; i++) {
            submit(form(null), "cle-site").andExpect(status().isAccepted());
        }
        submit(form(null), "cle-site").andExpect(status().isTooManyRequests());
        assertThat(jdbc.queryForObject("select count(*) from leads", Long.class)).isEqualTo(3);
    }

    @Test
    void corsAllowsOnlyTheWebsiteOnThePublicForm() throws Exception {
        mvc.perform(options("/api/public/leads").header(HttpHeaders.ORIGIN, "https://redseabunkering.com")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "x-api-key,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://redseabunkering.com"))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
        mvc.perform(options("/api/public/leads").header(HttpHeaders.ORIGIN, "https://malveillant.example")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")).andExpect(status().isForbidden());
        // Le site vitrine n'a accès à aucune autre route de l'API
        mvc.perform(options("/api/contacts").header(HttpHeaders.ORIGIN, "https://redseabunkering.com")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")).andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ tableau de bord

    @Test
    void dashboardComputesKpis() throws Exception {
        deal(sales, "RECUE", "Site web", 100, 500, null); // 50 000 × 10 %
        UUID nego = deal(sales, "NEGOCIATION", "Site web", 100, 1000, null); // 100 000 × 70 %
        jdbc.update("update deals set eta = ? where id = ?", today.plusDays(3), nego);
        deal(otherSales, "LIVREE", "LinkedIn", 40, 500, today);
        deal(otherSales, "PERDUE", null, 10, 500, today);
        lead("Site web", "NOUVEAU", null);

        JsonNode d = body(call(get("/api/dashboard"), sales).andExpect(status().isOk()));
        assertThat(d.get("weightedPipelineUsd").decimalValue()).isEqualByComparingTo("75000");
        assertThat(d.get("openAmountUsd").decimalValue()).isEqualByComparingTo("150000");
        assertThat(d.get("openCount").asLong()).isEqualTo(2);
        assertThat(d.get("requests30Days").asLong()).isEqualTo(5);
        assertThat(d.get("newLeads").asLong()).isEqualTo(1);
        assertThat(d.get("conversionRatePct").asInt()).isEqualTo(50);
        assertThat(d.get("deliveredMt90Days").decimalValue()).isEqualByComparingTo("40");
        assertThat(d.get("bySource").get(0).get("label").asText()).isEqualTo("Site web");
        assertThat(d.get("nextCalls")).hasSize(1);
    }

    // ------------------------------------------------------------------ rapports

    @Test
    void reportsAreScopedForSalesAndCompleteForManagers() throws Exception {
        deal(sales, "LIVREE", "Site web", 100, 500, today);
        UUID other = deal(otherSales, "LIVREE", "Site web", 300, 500, today);
        jdbc.update("update deals set created_at = now() - interval '10 days' where id = ?", other);
        deal(otherSales, "PERDUE", null, 10, 500, today);

        JsonNode mine = body(call(get("/api/reports/monthly-revenue"), sales).andExpect(status().isOk()));
        assertThat(mine).hasSize(6);
        assertThat(mine.get(5).get("revenueUsd").decimalValue()).isEqualByComparingTo("50000");
        assertThat(mine.get(5).get("month").asText()).isEqualTo(today.toString().substring(0, 7));
        JsonNode all = body(call(get("/api/reports/monthly-revenue"), manager).andExpect(status().isOk()));
        assertThat(all.get(5).get("revenueUsd").decimalValue()).isEqualByComparingTo("200000");

        call(get("/api/reports/lost-reasons"), sales).andExpect(jsonPath("$", hasSize(0)));
        call(get("/api/reports/lost-reasons"), manager).andExpect(jsonPath("$", hasSize(1)));

        JsonNode perfMine = body(call(get("/api/reports/sales-performance"), sales).andExpect(status().isOk()));
        assertThat(perfMine).hasSize(1);
        assertThat(perfMine.get(0).get("userId").asText()).isEqualTo(sales.getId().toString());
        JsonNode perf = body(call(get("/api/reports/sales-performance"), manager).andExpect(status().isOk()));
        JsonNode top = perf.get(0);
        assertThat(top.get("userId").asText()).isEqualTo(otherSales.getId().toString());
        assertThat(top.get("conversionRatePct").asInt()).isEqualTo(50);

        call(get("/api/reports/sales-cycle"), sales).andExpect(jsonPath("$.averageDays").value(0));
        call(get("/api/reports/sales-cycle"), manager).andExpect(jsonPath("$.delivered").value(2));
        JsonNode volumes = body(call(get("/api/reports/product-volumes"), sales).andExpect(status().isOk()));
        assertThat(volumes.findValues("volumeMt").stream().mapToDouble(JsonNode::asDouble).sum()).isEqualTo(100);
    }

    @Test
    void salesCycleIsEmptyWithoutDelivery() throws Exception {
        call(get("/api/reports/sales-cycle"), manager).andExpect(status().isOk())
                .andExpect(jsonPath("$.averageDays").doesNotExist()).andExpect(jsonPath("$.delivered").value(0));
    }

    @Test
    void funnelCountsContactsThatReachedEachStep() throws Exception {
        contact(sales, "LEAD");
        contact(sales, "SQL");
        contact(otherSales, "CLIENT");
        JsonNode all = body(call(get("/api/reports/funnel"), manager).andExpect(status().isOk()));
        assertThat(all.findValues("count").stream().map(JsonNode::asLong).toList()).containsExactly(3L, 2L, 2L, 1L, 1L);
        JsonNode mine = body(call(get("/api/reports/funnel"), sales).andExpect(status().isOk()));
        assertThat(mine.findValues("count").stream().map(JsonNode::asLong).toList()).containsExactly(2L, 1L, 1L, 0L, 0L);
    }

    @Test
    void reportsRequireAuthentication() throws Exception {
        mvc.perform(get("/api/dashboard")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/reports/funnel")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/marketing/summary")).andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ outils

    private ResultActions submit(Map<String, Object> form, String key) throws Exception {
        return mvc.perform(post("/api/public/leads").header("X-Api-Key", key).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(form)).with(r -> {
                    r.setRemoteAddr(ip);
                    return r;
                }));
    }

    private static Map<String, Object> form(String source) {
        Map<String, Object> f = new HashMap<>();
        f.put("name", "Sofia Marin");
        f.put("company", "Atlantico Bulk");
        f.put("email", "S.Marin@atlantico.example");
        f.put("vessel", "MV Atlantico Uno");
        f.put("fuelGrade", "VLSFO");
        f.put("quantityMt", 750);
        f.put("source", source);
        return f;
    }

    private ResultActions campaign(User as, String name, String channel, double spend, LocalDate start, LocalDate end)
            throws Exception {
        return call(post("/api/campaigns").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(campaignBody(name, channel, spend, start, end))), as);
    }

    private static Map<String, Object> campaignBody(String name, String channel, double spend, LocalDate start,
            LocalDate end) {
        Map<String, Object> m = new HashMap<>();
        m.put("name", name);
        m.put("channel", channel);
        m.put("spendUsd", spend);
        m.put("impressions", 0);
        m.put("clicks", 0);
        m.put("periodStart", start);
        m.put("periodEnd", end);
        return m;
    }

    private void lead(String source, String status, UUID dealId) {
        jdbc.update("insert into leads (id, name, source, status, converted_deal_id) values (?, 'L', ?, ?, ?)",
                UUID.randomUUID(), source, status, dealId);
    }

    private UUID deal(User owner, String stage, String source, double qty, double price, LocalDate closed) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into deals (id, vessel_name, fuel_grade, quantity_mt, price_usd_per_mt, stage, owner_id, source,
                                   closed_at, lost_reason)
                values (?, 'MV T', 'VLSFO', ?, ?, ?, ?, ?, ?, ?)""",
                id, qty, price, stage, owner.getId(), source, closed, "PERDUE".equals(stage) ? "Prix" : null);
        return id;
    }

    private void contact(User owner, String lifecycle) {
        jdbc.update("insert into contacts (id, first_name, last_name, lifecycle, owner_id) values (?, 'A', 'B', ?, ?)",
                UUID.randomUUID(), lifecycle, owner.getId());
    }

    private User user(Role role) {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        User u = new User(tag + "@report.test", "x", "Prenom", tag);
        u.setRole(role);
        u.markEmailVerified(Instant.now(), false);
        u.setStatus(UserStatus.ACTIVE);
        return users.save(u);
    }

    private ResultActions call(MockHttpServletRequestBuilder request, User as) throws Exception {
        return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.issue(as).value()));
    }

    private JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }
}
