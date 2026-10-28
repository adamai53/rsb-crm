package dj.redseabunkering.crm.contact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import dj.redseabunkering.crm.IntegrationTest;
import dj.redseabunkering.crm.auth.AccessTokenService;
import dj.redseabunkering.crm.user.Role;
import dj.redseabunkering.crm.user.User;
import dj.redseabunkering.crm.user.UserRepository;
import dj.redseabunkering.crm.user.UserStatus;

/** Référentiel : entreprises, contacts, droits par rôle, score, activités, import/export CSV, recherche. */
@AutoConfigureMockMvc
class ReferentialTests extends IntegrationTest {

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

    @BeforeEach
    void setUp() {
        tag = UUID.randomUUID().toString().substring(0, 6);
        sales = user(Role.SALES, "Hassan", "Doualeh");
        otherSales = user(Role.SALES, "Fatouma", "Ali");
        manager = user(Role.MANAGER, "Ibrahim", "Omar");
    }

    // ------------------------------------------------------------------ entreprises

    @Test
    void companyNamesAreUniqueIgnoringCase() throws Exception {
        String name = "Maersk Tankers " + tag;
        createCompany(sales, name).andExpect(status().isCreated());
        createCompany(otherSales, name.toUpperCase()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("déjà ce nom")));
    }

    @Test
    void companyListShowsDeliveredRevenueAndOpenDeals() throws Exception {
        UUID company = id(createCompany(sales, "Red Fleet " + tag));
        insertDeal(company, null, "MV OPEN " + tag, "1234567", 500, 612, "COTATION");
        insertDeal(company, null, "MV DONE " + tag, "7654321", 1000, 600, "LIVREE");
        insertDeal(company, null, "MV LOST " + tag, null, 800, 600, "PERDUE");

        as(sales, get("/api/companies").param("q", "Red Fleet " + tag))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].openDealsCount").value(1))
                .andExpect(jsonPath("$.content[0].deliveredVolumeMt").value(1000.0))
                .andExpect(jsonPath("$.content[0].revenueUsd").value(600000.0));
        as(sales, get("/api/companies/" + company))
                .andExpect(jsonPath("$.deals.length()").value(3))
                .andExpect(jsonPath("$.editable").value(true));
        as(otherSales, get("/api/companies/" + company)).andExpect(jsonPath("$.editable").value(false));
    }

    // ------------------------------------------------------------------ droits

    @Test
    void salesEditsOnlyTheirOwnContacts() throws Exception {
        UUID contact = id(createContact(sales, "Ali", "Hassan", "LEAD"));
        String update = contactJson("Ali", "Hassan-Modifié", "SQL", null);

        as(otherSales, put("/api/contacts/" + contact).contentType(MediaType.APPLICATION_JSON).content(update))
                .andExpect(status().isForbidden());
        as(otherSales, patch("/api/contacts/" + contact).contentType(MediaType.APPLICATION_JSON)
                .content("{\"lifecycle\":\"CLIENT\"}")).andExpect(status().isForbidden());
        as(otherSales, delete("/api/contacts/" + contact)).andExpect(status().isForbidden());

        as(sales, put("/api/contacts/" + contact).contentType(MediaType.APPLICATION_JSON).content(update))
                .andExpect(status().isNoContent());
        as(manager, patch("/api/contacts/" + contact).contentType(MediaType.APPLICATION_JSON)
                .content("{\"lifecycle\":\"OPPORTUNITE\"}")).andExpect(status().isNoContent());
        as(otherSales, get("/api/contacts/" + contact))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contact.lastName").value("Hassan-Modifié"))
                .andExpect(jsonPath("$.contact.lifecycle").value("OPPORTUNITE"))
                .andExpect(jsonPath("$.editable").value(false));
    }

    @Test
    void onlyManagersReassignRecords() throws Exception {
        // Un SALES ne peut ni créer pour un autre ni réattribuer, même son propre dossier
        as(sales, post("/api/contacts").contentType(MediaType.APPLICATION_JSON)
                .content(contactJson("X", "Y", "LEAD", otherSales.getId()))).andExpect(status().isForbidden());
        UUID contact = id(createContact(sales, "Moussa", "Ahmed", "LEAD"));
        String reassign = "{\"ownerId\":\"" + otherSales.getId() + "\"}";
        as(sales, patch("/api/contacts/" + contact).contentType(MediaType.APPLICATION_JSON).content(reassign))
                .andExpect(status().isForbidden());

        as(manager, patch("/api/contacts/" + contact).contentType(MediaType.APPLICATION_JSON).content(reassign))
                .andExpect(status().isNoContent());
        as(sales, get("/api/contacts/" + contact)).andExpect(jsonPath("$.contact.ownerId").value(otherSales.getId().toString()));

        // Impossible d'attribuer à un compte inactif
        User disabled = user(Role.SALES, "Ancien", "Commercial");
        disabled.setStatus(UserStatus.DISABLED);
        users.save(disabled);
        as(manager, patch("/api/contacts/" + contact).contentType(MediaType.APPLICATION_JSON)
                .content("{\"ownerId\":\"" + disabled.getId() + "\"}")).andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ liste, score, activités

    @Test
    void listFiltersCountsAndSortsByScore() throws Exception {
        UUID company = id(createCompany(sales, "Score Shipping " + tag));
        UUID hot = id(as(sales, post("/api/contacts").contentType(MediaType.APPLICATION_JSON).content("""
                {"firstName":"Chaud","lastName":"%s","title":"Bunker buyer","companyId":"%s",
                 "lifecycle":"CLIENT","source":"Recommandation"}""".formatted(tag, company))));
        id(as(sales, post("/api/contacts").contentType(MediaType.APPLICATION_JSON).content("""
                {"firstName":"Froid","lastName":"%s","title":"Capitaine","lifecycle":"LEAD","source":"Meta Ads"}"""
                .formatted(tag))));
        insertDeal(company, hot, "MV HOT " + tag, null, 1000, 612, "NEGOCIATION");
        for (int i = 0; i < 2; i++) {
            as(otherSales, post("/api/activities").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"type\":\"CALL\",\"text\":\"Appel %d\",\"contactId\":\"%s\"}".formatted(i, hot)))
                    .andExpect(status().isCreated());
        }

        as(sales, get("/api/contacts").param("q", tag).param("sort", "score").param("dir", "desc"))
                .andExpect(jsonPath("$.allCount").value(2))
                .andExpect(jsonPath("$.lifecycleCounts.CLIENT").value(1))
                .andExpect(jsonPath("$.lifecycleCounts.LEAD").value(1))
                .andExpect(jsonPath("$.content[0].firstName").value("Chaud"))
                // 20 + 28 + 10 (2 activités) + 18 (612 000 USD ouverts) + 12
                .andExpect(jsonPath("$.content[0].score").value(88))
                .andExpect(jsonPath("$.content[0].companyName").value("Score Shipping " + tag))
                .andExpect(jsonPath("$.content[1].score").value(6));

        as(sales, get("/api/contacts").param("q", tag).param("lifecycle", "LEAD"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.allCount").value(2));

        as(sales, get("/api/contacts/" + hot))
                .andExpect(jsonPath("$.score.total").value(88))
                .andExpect(jsonPath("$.score.parts[2].points").value(10))
                .andExpect(jsonPath("$.deals[0].vesselName").value("MV HOT " + tag));

        as(sales, get("/api/activities").param("contactId", hot.toString()))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].authorId").value(otherSales.getId().toString()));
    }

    @Test
    void systemActivitiesCannotBeForged() throws Exception {
        UUID contact = id(createContact(sales, "Sys", "Tem", "LEAD"));
        as(sales, post("/api/activities").contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"SYSTEM\",\"text\":\"Étape : X → Y\",\"contactId\":\"" + contact + "\"}"))
                .andExpect(status().isBadRequest());
        as(sales, post("/api/activities").contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"NOTE\",\"text\":\"Orpheline\",\"contactId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ import / export

    @Test
    void csvImportCreatesContactsAndCompaniesAndReportsProblems() throws Exception {
        String existingCompany = "Doraleh Agency " + tag;
        createCompany(manager, existingCompany).andExpect(status().isCreated());
        String csv = """
                Prénom;Nom;E-mail;Téléphone;Poste;Entreprise;Source
                Amina;Said;amina.%1$s@ship.dj;+253 77 11 22 33;Bunker buyer;%2$s;Salon / événement
                Omar;Farah;omar.%1$s@ship.dj;;Trader;NOUVEL ARMATEUR %1$s;LinkedIn
                Doublon;Fichier;AMINA.%1$s@ship.dj;;;;
                Mauvais;Mail;pas-un-email;;;;
                ;;;;;;
                Sans;Mail;;;;nouvel armateur %1$s;
                """.formatted(tag, existingCompany.toLowerCase());

        as(sales, multipart("/api/contacts/import")
                .file(new MockMultipartFile("file", "contacts.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows").value(5))
                .andExpect(jsonPath("$.created").value(3))
                .andExpect(jsonPath("$.skipped").value(2))
                .andExpect(jsonPath("$.companiesCreated").value(1))
                .andExpect(jsonPath("$.issues[*].line").value(org.hamcrest.Matchers.contains(4, 5)))
                .andExpect(jsonPath("$.issues[0].message").value(containsString("déjà existant")));

        as(sales, get("/api/contacts").param("q", "amina." + tag))
                .andExpect(jsonPath("$.content[0].companyName").value(existingCompany))
                .andExpect(jsonPath("$.content[0].ownerId").value(sales.getId().toString()))
                .andExpect(jsonPath("$.content[0].lifecycle").value("LEAD"))
                .andExpect(jsonPath("$.content[0].phone").value("+253 77 11 22 33"));
        as(sales, get("/api/companies").param("q", "nouvel armateur " + tag))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].contactsCount").value(2));
    }

    @Test
    void csvWithoutNameColumnsIsRejected() throws Exception {
        as(sales, multipart("/api/contacts/import").file(new MockMultipartFile("file", "x.csv", "text/csv",
                "a,b\n1,2\n".getBytes(StandardCharsets.UTF_8))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("prenom")));
    }

    @Test
    void exportIsExcelFriendlyAndReimportable() throws Exception {
        createContact(sales, "Export", "Test" + tag, "MQL");
        byte[] body = as(sales, get("/api/contacts/export").param("q", "Test" + tag))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string(HttpHeaders.CONTENT_DISPOSITION, containsString("contacts-rsb.csv")))
                .andReturn().getResponse().getContentAsByteArray();
        String csv = new String(body, StandardCharsets.UTF_8);
        assertThat(csv).startsWith("﻿prenom;nom;email;telephone;poste;entreprise;source");
        assertThat(csv).contains("Export;Test" + tag).contains(";MQL;").contains("Hassan Doualeh");
    }

    // ------------------------------------------------------------------ recherche

    @Test
    void globalSearchFindsContactsCompaniesVesselsAndImo() throws Exception {
        UUID company = id(createCompany(sales, "Zeyla Marine " + tag));
        createContact(sales, "Zeyla", "Contact" + tag, "LEAD");
        insertDeal(company, null, "MV ZEYLA " + tag, "9312345", 300, 845, "RECUE");

        as(sales, get("/api/search").param("q", "zeyla"))
                .andExpect(jsonPath("$[*].type").value(hasItem("contact")))
                .andExpect(jsonPath("$[*].type").value(hasItem("company")))
                .andExpect(jsonPath("$[*].type").value(hasItem("deal")));
        as(sales, get("/api/search").param("q", "9312345"))
                .andExpect(jsonPath("$[0].label").value("MV ZEYLA " + tag))
                .andExpect(jsonPath("$[0].sub").value(containsString("IMO 9312345")));
        as(sales, get("/api/search").param("q", "z")).andExpect(jsonPath("$.length()").value(0));
        // Les jokers SQL saisis par l'utilisateur sont pris au pied de la lettre
        as(sales, get("/api/search").param("q", "%%")).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void referenceDataListsSourcesStagesAndActiveOwners() throws Exception {
        as(sales, get("/api/reference"))
                .andExpect(jsonPath("$.sources[0]").value("Site web"))
                .andExpect(jsonPath("$.stages.length()").value(7))
                .andExpect(jsonPath("$.companyTypes[1].label").value("Ship manager"))
                .andExpect(jsonPath("$.users[*].name").value(hasItem("Hassan Doualeh")));
    }

    // ------------------------------------------------------------------ helpers

    private User user(Role role, String first, String last) {
        User u = new User(first.toLowerCase() + "." + tag + "." + role.name().toLowerCase() + "@test.rsb", "x", first, last);
        u.setRole(role);
        u.markEmailVerified(Instant.now(), false);
        u.setStatus(UserStatus.ACTIVE);
        return users.save(u);
    }

    private ResultActions as(User u, MockHttpServletRequestBuilder req) throws Exception {
        return mvc.perform(req.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.issue(u).value()));
    }

    private ResultActions createCompany(User u, String name) throws Exception {
        return as(u, post("/api/companies").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\",\"type\":\"ARMATEUR\",\"country\":\"Djibouti\"}"));
    }

    private ResultActions createContact(User u, String first, String last, String lifecycle) throws Exception {
        return as(u, post("/api/contacts").contentType(MediaType.APPLICATION_JSON)
                .content(contactJson(first, last, lifecycle, null))).andExpect(status().isCreated());
    }

    private static String contactJson(String first, String last, String lifecycle, UUID owner) {
        return """
                {"firstName":"%s","lastName":"%s","lifecycle":"%s","source":"Site web"%s}"""
                .formatted(first, last, lifecycle, owner == null ? "" : ",\"ownerId\":\"" + owner + "\"");
    }

    private UUID id(ResultActions created) throws Exception {
        String body = created.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(json.readTree(body).get("id").asText());
    }

    private void insertDeal(UUID company, UUID contact, String vessel, String imo, int qty, int price, String stage) {
        jdbc.update("""
                insert into deals (id, vessel_name, imo, company_id, contact_id, fuel_grade, quantity_mt,
                                   price_usd_per_mt, stage, owner_id)
                values (gen_random_uuid(), ?, ?, ?, ?, 'VLSFO', ?, ?, ?, ?)""",
                vessel, imo, company, contact, qty, price, stage, sales.getId());
    }
}
