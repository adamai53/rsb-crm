package dj.redseabunkering.crm.deal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import dj.redseabunkering.crm.IntegrationTest;
import dj.redseabunkering.crm.auth.AccessTokenService;
import dj.redseabunkering.crm.user.Role;
import dj.redseabunkering.crm.user.User;
import dj.redseabunkering.crm.user.UserRepository;
import dj.redseabunkering.crm.user.UserStatus;

/** Ventes : règles métier 1 à 7, droits, tâches et calendrier des escales. */
@AutoConfigureMockMvc
class SalesRulesTests extends IntegrationTest {

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

    private User sales;
    private User otherSales;
    private User manager;
    private String tag;
    private final LocalDate today = LocalDate.now(ZoneId.of("Africa/Djibouti"));

    @BeforeEach
    void setUp() {
        tag = UUID.randomUUID().toString().substring(0, 6);
        sales = user(Role.SALES, "Hassan", "Doualeh");
        otherSales = user(Role.SALES, "Fatouma", "Ali");
        manager = user(Role.MANAGER, "Ibrahim", "Omar");
    }

    // ------------------------------------------------------------------ règles 4 à 7

    @Test
    void stageChangeLogsSystemActivityAndAudit() throws Exception {
        UUID contact = contact(sales, "LEAD");
        UUID deal = deal(sales, contact, "RECUE", 850, null);

        stage(sales, deal, "QUALIFIEE", null).andExpect(status().isNoContent());

        assertThat(jdbc.queryForList("select text from activities where deal_id = ? and type = 'SYSTEM' order by occurred_at",
                String.class, deal)).containsExactly("Transaction créée : MV TEST " + tag + " · VLSFO 850 MT",
                        "Étape : Demande reçue → Qualifiée");
        Map<String, Object> audit = jdbc.queryForMap("""
                select actor_id, details->>'from' as f, details->>'to' as t from audit_log
                where entity_id = ? and action = 'DEAL_STAGE_CHANGED'""", deal);
        assertThat(audit).containsEntry("actor_id", sales.getId()).containsEntry("f", "RECUE").containsEntry("t", "QUALIFIEE");
        // Règle 6 : étape ouverte → contact au moins Opportunité
        assertThat(lifecycle(contact)).isEqualTo("OPPORTUNITE");
    }

    @Test
    void deliveryClosesDealAndMakesContactClient() throws Exception {
        UUID contact = contact(sales, "SQL");
        UUID deal = deal(sales, contact, "NOMINATION", 500, null);

        stage(sales, deal, "LIVREE", null).andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("select closed_at from deals where id = ?", LocalDate.class, deal)).isEqualTo(today);
        assertThat(lifecycle(contact)).isEqualTo("CLIENT");
        as(sales, get("/api/companies/" + companyOfDeal(deal)))
                .andExpect(jsonPath("$.company.revenueUsd").value(306000.0));
    }

    @Test
    void lostRequiresAReasonAndReopeningClearsIt() throws Exception {
        UUID contact = contact(sales, "CLIENT");
        UUID deal = deal(sales, contact, "COTATION", 400, null);

        stage(sales, deal, "PERDUE", null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("motif")));
        stage(sales, deal, "PERDUE", "Prix concurrent inférieur").andExpect(status().isNoContent());
        Map<String, Object> lost = jdbc.queryForMap("select closed_at, lost_reason from deals where id = ?", deal);
        assertThat(lost.get("lost_reason")).isEqualTo("Prix concurrent inférieur");
        assertThat(lost.get("closed_at")).isNotNull();
        assertThat(jdbc.queryForObject("select text from activities where deal_id = ? order by occurred_at desc limit 1",
                String.class, deal)).isEqualTo("Étape : Cotation envoyée → Perdue (motif : Prix concurrent inférieur)");

        stage(sales, deal, "NEGOCIATION", null).andExpect(status().isNoContent());
        Map<String, Object> reopened = jdbc.queryForMap("select closed_at, lost_reason from deals where id = ?", deal);
        assertThat(reopened.get("closed_at")).isNull();
        assertThat(reopened.get("lost_reason")).isNull();
        // Un client reste client : « au moins Opportunité » ne fait jamais redescendre
        assertThat(lifecycle(contact)).isEqualTo("CLIENT");
    }

    @Test
    void onlyOwnerOrManagerMovesADeal() throws Exception {
        UUID deal = deal(sales, null, "RECUE", 100, null);
        stage(otherSales, deal, "QUALIFIEE", null).andExpect(status().isForbidden());
        stage(manager, deal, "QUALIFIEE", null).andExpect(status().isNoContent());
        as(otherSales, patch("/api/deals/" + deal + "/owner").contentType(MediaType.APPLICATION_JSON)
                .content("{\"ownerId\":\"" + otherSales.getId() + "\"}")).andExpect(status().isForbidden());
        as(manager, patch("/api/deals/" + deal + "/owner").contentType(MediaType.APPLICATION_JSON)
                .content("{\"ownerId\":\"" + otherSales.getId() + "\"}")).andExpect(status().isNoContent());
    }

    @Test
    void newDealUsesReferencePriceAndCannotStartClosed() throws Exception {
        UUID deal = id(as(sales, post("/api/deals").contentType(MediaType.APPLICATION_JSON).content("""
                {"vesselName":"MT PRICE %s","fuelGrade":"MGO","quantityMt":200,"eta":"%s","deliveryLocation":"DMP"}"""
                .formatted(tag, today.plusDays(5))))
                .andExpect(status().isCreated()));
        as(sales, get("/api/deals/" + deal))
                .andExpect(jsonPath("$.deal.priceUsdPerMt").value(845.0))
                .andExpect(jsonPath("$.deal.amountUsd").value(169000.0))
                .andExpect(jsonPath("$.deal.stage").value("RECUE"))
                .andExpect(jsonPath("$.deal.deliveryLocationLabel").value("Port de Doraleh (DMP)"));
        as(sales, post("/api/deals").contentType(MediaType.APPLICATION_JSON)
                .content("{\"vesselName\":\"X\",\"fuelGrade\":\"MGO\",\"quantityMt\":1,\"stage\":\"LIVREE\"}"))
                .andExpect(status().isBadRequest());
        as(sales, post("/api/deals").contentType(MediaType.APPLICATION_JSON)
                .content("{\"vesselName\":\"X\",\"fuelGrade\":\"MGO\",\"quantityMt\":1,\"imo\":\"12345\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.imo").value(containsString("7 chiffres")));
    }

    // ------------------------------------------------------------------ règles 1 et 2 via l'API

    @Test
    void dealListSummaryComputesWeightedPipelineAndConversion() throws Exception {
        deal(sales, null, "NEGOCIATION", 850, 612);   // 520 200 × 70 %
        deal(sales, null, "COTATION", 300, 845);      // 253 500 × 50 %
        UUID won = deal(sales, null, "NOMINATION", 100, 600);
        stage(sales, won, "LIVREE", null);
        UUID lost = deal(sales, null, "RECUE", 100, 600);
        stage(sales, lost, "PERDUE", "Autre");

        as(sales, get("/api/deals").param("ownerId", sales.getId().toString()))
                .andExpect(jsonPath("$.summary.openCount").value(2))
                .andExpect(jsonPath("$.summary.openAmountUsd").value(773700.0))
                .andExpect(jsonPath("$.summary.weightedPipelineUsd").value(490890.0))
                .andExpect(jsonPath("$.summary.conversionRatePct").value(50))
                .andExpect(jsonPath("$.content.length()").value(4));
    }

    // ------------------------------------------------------------------ règle 3 : conversion d'un lead

    @Test
    void leadConversionCreatesEverythingForTheLeastLoadedSalesperson() throws Exception {
        // Seuls deux commerciaux actifs : le plus chargé a une transaction ouverte, l'autre aucune
        jdbc.update("update users set status = 'DISABLED' where role = 'SALES' and id not in (?, ?)",
                sales.getId(), otherSales.getId());
        deal(sales, null, "COTATION", 100, 600);
        String companyName = "Atlas Med Shipping " + tag;
        jdbc.update("insert into companies (id, name, type) values (gen_random_uuid(), ?, 'ARMATEUR')", companyName);

        UUID lead = id(as(manager, post("/api/leads").contentType(MediaType.APPLICATION_JSON).content("""
                {"name":"Youssef Benali","companyName":"%s","email":"y.benali.%s@atlasmed.example",
                 "vesselName":"MV ATLAS HORIZON","imo":"9301234","fuelGrade":"VLSFO","quantityMt":800,
                 "eta":"%s","deliveryLocation":"ANCHORAGE","source":"Google Ads","message":"Besoin urgent"}"""
                .formatted(companyName.toUpperCase(), tag, today.plusDays(6)))).andExpect(status().isCreated()));
        as(manager, get("/api/leads/" + lead)).andExpect(jsonPath("$.estimatedValueUsd").value(489600.0));

        String res = as(manager, post("/api/leads/" + lead + "/convert"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerId").value(otherSales.getId().toString()))
                .andExpect(jsonPath("$.companyCreated").value(false))
                .andExpect(jsonPath("$.contactCreated").value(true))
                .andReturn().getResponse().getContentAsString();
        UUID dealId = UUID.fromString(json.readTree(res).get("dealId").asText());

        Map<String, Object> deal = jdbc.queryForMap("""
                select d.stage, d.price_usd_per_mt, d.owner_id, co.name as company, c.first_name, c.last_name,
                       c.lifecycle, c.owner_id as contact_owner, c.source
                from deals d join companies co on co.id = d.company_id join contacts c on c.id = d.contact_id
                where d.id = ?""", dealId);
        assertThat(deal).containsEntry("stage", "QUALIFIEE").containsEntry("owner_id", otherSales.getId())
                .containsEntry("company", companyName).containsEntry("first_name", "Youssef")
                .containsEntry("last_name", "Benali").containsEntry("lifecycle", "SQL")
                .containsEntry("contact_owner", otherSales.getId()).containsEntry("source", "Google Ads");
        assertThat(((java.math.BigDecimal) deal.get("price_usd_per_mt"))).isEqualByComparingTo("612");

        Map<String, Object> task = jdbc.queryForMap("select title, priority, due_date, owner_id from tasks where deal_id = ?", dealId);
        assertThat(task).containsEntry("title", "Envoyer la cotation à Youssef Benali").containsEntry("priority", "HAUTE")
                .containsEntry("owner_id", otherSales.getId());
        assertThat(((java.sql.Date) task.get("due_date")).toLocalDate()).isEqualTo(today);
        assertThat(jdbc.queryForObject("select text from activities where deal_id = ? and type = 'SYSTEM'", String.class, dealId))
                .isEqualTo("Lead converti depuis Google Ads. Message : « Besoin urgent »");
        assertThat(jdbc.queryForMap("select status, converted_deal_id from leads where id = ?", lead))
                .containsEntry("status", "CONVERTI").containsEntry("converted_deal_id", dealId);

        as(manager, post("/api/leads/" + lead + "/convert")).andExpect(status().isConflict());
        as(manager, post("/api/leads/" + lead + "/reject")).andExpect(status().isConflict());
    }

    @Test
    void conversionCreatesUnknownCompanyAndRejectWorks() throws Exception {
        UUID lead = id(as(sales, post("/api/leads").contentType(MediaType.APPLICATION_JSON).content("""
                {"name":"Grace Odhiambo","companyName":"Kilindini Lines %s","email":"grace@kilindini.example",
                 "vesselName":"MV MOMBASA STAR","fuelGrade":"MGO","quantityMt":160,"source":"Site web"}"""
                .formatted(tag))).andExpect(status().isCreated()));
        as(sales, post("/api/leads/" + lead + "/convert"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.companyCreated").value(true));
        assertThat(jdbc.queryForObject("select website from companies where name = ?", String.class,
                "Kilindini Lines " + tag)).isEqualTo("kilindini.example");

        UUID spam = id(as(sales, post("/api/leads").contentType(MediaType.APPLICATION_JSON).content("""
                {"name":"Spam","vesselName":"?","fuelGrade":"MGO","quantityMt":1}""")).andExpect(status().isCreated()));
        as(sales, post("/api/leads/" + spam + "/reject")).andExpect(status().isNoContent());
        as(sales, get("/api/leads").param("status", "REJETE"))
                .andExpect(jsonPath("$[?(@.id == '" + spam + "')]").exists());
    }

    // ------------------------------------------------------------------ tâches et escales

    @Test
    void tasksInheritDealLinksAndRespectOwnership() throws Exception {
        UUID contact = contact(sales, "SQL");
        UUID deal = deal(sales, contact, "COTATION", 300, null);
        as(sales, post("/api/tasks").contentType(MediaType.APPLICATION_JSON).content("""
                {"title":"Pour un collègue","dueDate":"%s","priority":"BASSE","ownerId":"%s"}"""
                .formatted(today, otherSales.getId()))).andExpect(status().isForbidden());
        UUID task = id(as(sales, post("/api/tasks").contentType(MediaType.APPLICATION_JSON).content("""
                {"title":"Relancer MV TEST","dueDate":"%s","priority":"HAUTE","dealId":"%s"}"""
                .formatted(today.minusDays(1), deal))).andExpect(status().isCreated()));
        assertThat(jdbc.queryForObject("select contact_id from tasks where id = ?", UUID.class, task)).isEqualTo(contact);

        as(sales, get("/api/tasks/due-count")).andExpect(jsonPath("$.due").value(1));
        as(otherSales, patch("/api/tasks/" + task + "/done").contentType(MediaType.APPLICATION_JSON)
                .content("{\"done\":true}")).andExpect(status().isForbidden());
        as(sales, patch("/api/tasks/" + task + "/done").contentType(MediaType.APPLICATION_JSON)
                .content("{\"done\":true}")).andExpect(status().isNoContent());
        as(sales, get("/api/tasks/due-count")).andExpect(jsonPath("$.due").value(0));
        as(sales, get("/api/deals/" + deal)).andExpect(jsonPath("$.tasks[0].done").value(true));
    }

    @Test
    void upcomingCallsCoverTheNextThreeWeeksWithoutLostDeals() throws Exception {
        UUID soon = deal(sales, null, "NOMINATION", 100, null);
        UUID lost = deal(sales, null, "COTATION", 100, null);
        UUID far = deal(sales, null, "RECUE", 100, null);
        jdbc.update("update deals set eta = ? where id = ?", today.plusDays(2), soon);
        jdbc.update("update deals set eta = ? where id = ?", today.plusDays(3), lost);
        jdbc.update("update deals set eta = ? where id = ?", today.plusDays(30), far);
        stage(sales, lost, "PERDUE", "Délai de livraison");

        String body = as(sales, get("/api/calls/upcoming")).andExpect(status().isOk())
                .andExpect(jsonPath("$.today").value(today.toString()))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).contains(soon.toString()).doesNotContain(lost.toString()).doesNotContain(far.toString());
    }

    // ------------------------------------------------------------------ helpers

    private User user(Role role, String first, String last) {
        User u = new User(first.toLowerCase() + "." + tag + "." + role.name().toLowerCase() + "@sales.test", "x", first, last);
        u.setRole(role);
        u.markEmailVerified(Instant.now(), false);
        u.setStatus(UserStatus.ACTIVE);
        return users.save(u);
    }

    private ResultActions as(User u, MockHttpServletRequestBuilder req) throws Exception {
        return mvc.perform(req.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.issue(u).value()));
    }

    private UUID id(ResultActions created) throws Exception {
        return UUID.fromString(json.readTree(created.andReturn().getResponse().getContentAsString()).get("id").asText());
    }

    private UUID contact(User owner, String lifecycle) throws Exception {
        UUID company = id(as(owner, post("/api/companies").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Co " + UUID.randomUUID() + "\",\"type\":\"ARMATEUR\"}")));
        return id(as(owner, post("/api/contacts").contentType(MediaType.APPLICATION_JSON).content("""
                {"firstName":"Client","lastName":"%s","lifecycle":"%s","companyId":"%s"}""".formatted(tag, lifecycle, company)))
                .andExpect(status().isCreated()));
    }

    private UUID deal(User owner, UUID contact, String stage, int qty, Integer price) throws Exception {
        return id(as(owner, post("/api/deals").contentType(MediaType.APPLICATION_JSON).content("""
                {"vesselName":"MV TEST %s","fuelGrade":"VLSFO","quantityMt":%d%s%s,"stage":"%s"}""".formatted(tag, qty,
                price == null ? "" : ",\"priceUsdPerMt\":" + price,
                contact == null ? "" : ",\"contactId\":\"" + contact + "\"", stage)))
                .andExpect(status().isCreated()));
    }

    private ResultActions stage(User u, UUID deal, String stage, String reason) throws Exception {
        String body = reason == null ? "{\"stage\":\"" + stage + "\"}"
                : "{\"stage\":\"" + stage + "\",\"lostReason\":\"" + reason + "\"}";
        return as(u, patch("/api/deals/" + deal + "/stage").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String lifecycle(UUID contact) {
        return jdbc.queryForObject("select lifecycle from contacts where id = ?", String.class, contact);
    }

    private UUID companyOfDeal(UUID deal) {
        return jdbc.queryForObject("select company_id from deals where id = ?", UUID.class, deal);
    }
}
