package dj.redseabunkering.crm.quote;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;

import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import dj.redseabunkering.crm.IntegrationTest;
import dj.redseabunkering.crm.auth.AccessTokenService;
import dj.redseabunkering.crm.email.MergeFields;
import dj.redseabunkering.crm.sequence.SequenceService;
import dj.redseabunkering.crm.user.Role;
import dj.redseabunkering.crm.user.User;
import dj.redseabunkering.crm.user.UserRepository;
import dj.redseabunkering.crm.user.UserStatus;

/** Devis et e-mails : règles 8 à 14, avec un vrai message MIME intercepté à la place du serveur SMTP. */
@AutoConfigureMockMvc
class QuotesAndEmailsTests extends IntegrationTest {

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

    @Autowired
    QuoteService quoteService;

    @Autowired
    SequenceService sequenceService;

    @MockitoBean
    JavaMailSender mail;

    private User sales;
    private User other;
    private String tag;
    private UUID contact;
    private UUID deal;
    private final LocalDate today = LocalDate.now(ZoneId.of("Africa/Djibouti"));

    @BeforeEach
    void setUp() throws Exception {
        reset(mail);
        when(mail.createMimeMessage()).thenAnswer(i -> new MimeMessage(Session.getInstance(new Properties())));
        tag = UUID.randomUUID().toString().substring(0, 6);
        sales = user("Hassan", "Doualeh");
        other = user("Fatouma", "Ali");
        UUID company = id(as(sales, post("/api/companies").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Red Fleet " + tag + "\",\"type\":\"ARMATEUR\"}")));
        contact = id(as(sales, post("/api/contacts").contentType(MediaType.APPLICATION_JSON).content("""
                {"firstName":"Amina","lastName":"Said","email":"amina.%s@ship.example","lifecycle":"SQL","companyId":"%s"}"""
                .formatted(tag, company))));
        deal = id(as(sales, post("/api/deals").contentType(MediaType.APPLICATION_JSON).content("""
                {"vesselName":"MV KESTREL %s","imo":"9301234","contactId":"%s","fuelGrade":"VLSFO","quantityMt":850,
                 "priceUsdPerMt":612,"stage":"QUALIFIEE","eta":"%s","deliveryLocation":"DMP"}"""
                .formatted(tag, contact, today.plusDays(6)))));
    }

    // ------------------------------------------------------------------ règle 10

    @Test
    void quoteDefaultsAndYearlySequentialNumber() throws Exception {
        UUID first = quote(null, null);
        UUID second = quote(600, 0);
        int year = today.getYear();
        String n1 = number(first);
        assertThat(n1).matches("RSB-Q-" + year + "-\\d{4}");
        assertThat(Integer.parseInt(number(second).substring(11))).isEqualTo(Integer.parseInt(n1.substring(11)) + 1);

        as(sales, get("/api/quotes/" + first))
                .andExpect(jsonPath("$.status").value("BROUILLON"))
                .andExpect(jsonPath("$.priceUsdPerMt").value(612.0))
                .andExpect(jsonPath("$.bargeFeeUsd").value(1450.0))
                .andExpect(jsonPath("$.totalUsd").value(521650.0));
        as(sales, get("/api/quotes/" + second)).andExpect(jsonPath("$.bargeFeeUsd").value(0.0));

        Instant validUntil = jdbc.queryForObject("select valid_until from quotes where id = ?", java.sql.Timestamp.class, first).toInstant();
        assertThat(validUntil).isBetween(Instant.now().plusSeconds(47 * 3600), Instant.now().plusSeconds(48 * 3600 + 5));
    }

    @Test
    void pdfContainsTheQuoteDetails() throws Exception {
        UUID q = quote(null, null);
        byte[] pdf = as(sales, get("/api/quotes/" + q + "/pdf")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
        String text = new PdfTextExtractor(new PdfReader(pdf)).getTextFromPage(1);
        assertThat(text).contains(number(q)).contains("MV KESTREL " + tag).contains("VLSFO 0,5 % S").contains("Port de Doraleh")
                .contains("Frais de barge").contains("Total hors taxes").contains("Amina Said");
        assertThat(text.replaceAll("[\s\u00A0]+", " ")).contains("521 650");
    }

    // ------------------------------------------------------------------ règle 8

    @Test
    void sendingEmailsThePdfAdvancesTheDealAndLogsActivity() throws Exception {
        UUID q = quote(null, null);
        as(sales, get("/api/quotes/" + q + "/email-draft"))
                .andExpect(jsonPath("$.subject").value("Cotation VLSFO 0,5 % S — MV KESTREL " + tag))
                .andExpect(jsonPath("$.body").value(containsString("Bonjour Amina,")))
                .andExpect(jsonPath("$.body").value(containsString("de VLSFO 0,5 % S")));

        send(q, "Votre cotation", "Bonjour,\nCi-joint <notre> cotation.").andExpect(status().isNoContent());

        var captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mail).send(captor.capture());
        MimeMessage message = captor.getValue();
        assertThat(message.getAllRecipients()[0].toString()).isEqualTo("amina." + tag + "@ship.example");
        assertThat(message.getSubject()).isEqualTo("Votre cotation");
        assertThat(message.getReplyTo()[0].toString()).isEqualTo(sales.getEmail());
        var parts = new ArrayList<Part>();
        collect(message, parts);
        assertThat(parts).anyMatch(p -> {
            try {
                return ("Devis-" + number(q) + ".pdf").equals(p.getFileName());
            } catch (Exception e) {
                return false;
            }
        });
        String html = parts.stream().map(QuotesAndEmailsTests::text).filter(t -> t.contains("/api/public/t/")).findFirst().orElseThrow();
        assertThat(html).contains("&lt;notre&gt;").doesNotContain("<notre>");

        assertThat(quoteStatus(q)).isEqualTo("ENVOYE");
        assertThat(dealStage()).isEqualTo("COTATION");
        assertThat(jdbc.queryForObject("select count(*) from activities where deal_id = ? and type = 'EMAIL'", Integer.class, deal)).isEqualTo(1);
        assertThat(jdbc.queryForList("select text from activities where deal_id = ? and type = 'SYSTEM'", String.class, deal))
                .contains("Étape : Qualifiée → Cotation envoyée");
    }

    @Test
    void sendingDoesNotMoveADealAlreadyPastCotation() throws Exception {
        moveDeal("NEGOCIATION");
        send(quote(null, null), "Objet", "Corps").andExpect(status().isNoContent());
        assertThat(dealStage()).isEqualTo("NEGOCIATION");
    }

    @Test
    void smtpFailureChangesNothingButIsTraced() throws Exception {
        UUID q = quote(null, null);
        doThrow(new MailSendException("connexion refusée")).when(mail).send(any(MimeMessage.class));

        send(q, "Objet", "Corps").andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.detail").value(containsString("n'a pas pu être envoyé")));

        assertThat(quoteStatus(q)).isEqualTo("BROUILLON");
        assertThat(dealStage()).isEqualTo("QUALIFIEE");
        assertThat(jdbc.queryForMap("select status, error_message from sent_emails where quote_id = ?", q))
                .containsEntry("status", "ERREUR").containsEntry("error_message", "connexion refusée");
        assertThat(jdbc.queryForObject("select count(*) from activities where deal_id = ? and type = 'EMAIL'", Integer.class, deal)).isZero();
    }

    @Test
    void contactWithoutEmailIsRefused() throws Exception {
        UUID noMail = id(as(sales, post("/api/contacts").contentType(MediaType.APPLICATION_JSON)
                .content("{\"firstName\":\"Sans\",\"lastName\":\"Mail\",\"lifecycle\":\"LEAD\"}")));
        UUID d = id(as(sales, post("/api/deals").contentType(MediaType.APPLICATION_JSON).content("""
                {"vesselName":"MV X","contactId":"%s","fuelGrade":"MGO","quantityMt":10}""".formatted(noMail))));
        UUID q = id(as(sales, post("/api/quotes").contentType(MediaType.APPLICATION_JSON).content("{\"dealId\":\"" + d + "\"}")));
        send(q, "Objet", "Corps").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("pas d'adresse e-mail")));
        verify(mail, never()).send(any(MimeMessage.class));
    }

    // ------------------------------------------------------------------ règle 9

    @Test
    void acceptingCopiesThePriceAndMovesToNomination() throws Exception {
        UUID q = quote(598, null);
        as(sales, post("/api/quotes/" + q + "/accept")).andExpect(status().isNoContent());
        assertThat(quoteStatus(q)).isEqualTo("ACCEPTE");
        Map<String, Object> d = jdbc.queryForMap("select stage, price_usd_per_mt, amount_usd from deals where id = ?", deal);
        assertThat(d.get("stage")).isEqualTo("NOMINATION");
        assertThat((BigDecimal) d.get("price_usd_per_mt")).isEqualByComparingTo("598");
        assertThat((BigDecimal) d.get("amount_usd")).isEqualByComparingTo("508300");
        as(sales, post("/api/quotes/" + q + "/accept")).andExpect(status().isConflict());
        as(sales, post("/api/quotes/" + q + "/refuse")).andExpect(status().isConflict());
    }

    @Test
    void expiredQuotesAndClosedDealsAreRefused() throws Exception {
        UUID q = quote(null, null);
        send(q, "Objet", "Corps").andExpect(status().isNoContent());
        jdbc.update("update quotes set valid_until = now() - interval '1 hour' where id = ?", q);
        as(sales, post("/api/quotes/" + q + "/accept")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("expiré")));
        quoteService.expireOverdue();
        assertThat(quoteStatus(q)).isEqualTo("EXPIRE");

        UUID q2 = quote(null, null);
        as(sales, patch("/api/deals/" + deal + "/stage").contentType(MediaType.APPLICATION_JSON)
                .content("{\"stage\":\"PERDUE\",\"lostReason\":\"Autre\"}")).andExpect(status().isNoContent());
        as(sales, post("/api/quotes/" + q2 + "/accept")).andExpect(status().isConflict());
        as(sales, post("/api/quotes").contentType(MediaType.APPLICATION_JSON).content("{\"dealId\":\"" + deal + "\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void onlyTheOwnerOrAManagerHandlesQuotes() throws Exception {
        as(other, post("/api/quotes").contentType(MediaType.APPLICATION_JSON).content("{\"dealId\":\"" + deal + "\"}"))
                .andExpect(status().isForbidden());
        UUID q = quote(null, null);
        as(other, post("/api/quotes/" + q + "/accept")).andExpect(status().isForbidden());
        as(other, post("/api/quotes/" + q + "/send").contentType(MediaType.APPLICATION_JSON)
                .content("{\"subject\":\"S\",\"body\":\"B\"}")).andExpect(status().isForbidden());
        as(other, get("/api/quotes/" + q)).andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ règles 13 et 14

    @Test
    void mergeFieldsAreReplacedAndUnknownOnesRemoved() {
        assertThat(MergeFields.merge("Bonjour {{prenom}}, {{inconnu}}coût 100 $ pour {{navire}}",
                Map.of("prenom", "Amina", "navire", "MV $1")))
                .isEqualTo("Bonjour Amina, coût 100 $ pour MV $1");
    }

    @Test
    void trackingPixelMarksTheEmailOpenedOnceWithoutAuthentication() throws Exception {
        UUID template = jdbc.queryForObject("select id from email_templates where name = 'Accusé de réception'", UUID.class);
        as(sales, get("/api/emails/draft").param("templateId", template.toString()).param("contactId", contact.toString()))
                .andExpect(jsonPath("$.subject").value("Votre demande de soutage pour MV KESTREL " + tag))
                .andExpect(jsonPath("$.dealId").value(deal.toString()));
        as(other, post("/api/emails/send").contentType(MediaType.APPLICATION_JSON).content(
                json.writeValueAsString(Map.of("contactId", contact, "dealId", deal, "subject", "Bonjour " + tag, "body", "Texte"))))
                .andExpect(status().isNoContent());
        UUID tracking = jdbc.queryForObject("select tracking_id from sent_emails where subject = ?", UUID.class, "Bonjour " + tag);

        mvc.perform(get("/api/public/t/" + tracking + ".png")).andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().contentType(MediaType.IMAGE_PNG));
        as(sales, get("/api/emails/sent").param("contactId", contact.toString()))
                .andExpect(jsonPath("$[0].status").value("OUVERT"))
                .andExpect(jsonPath("$[0].openedAt").isNotEmpty());
        Object firstOpen = jdbc.queryForObject("select opened_at from sent_emails where tracking_id = ?", Object.class, tracking);
        mvc.perform(get("/api/public/t/" + tracking + ".png")).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select opened_at from sent_emails where tracking_id = ?", Object.class, tracking)).isEqualTo(firstOpen);
        mvc.perform(get("/api/public/t/pas-un-uuid.png")).andExpect(status().isOk());
        mvc.perform(get("/api/public/t/" + UUID.randomUUID() + ".png")).andExpect(status().isOk());
    }

    @Test
    void onlyAdminsEditTemplates() throws Exception {
        as(sales, get("/api/email-templates")).andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(5)));
        as(sales, post("/api/email-templates").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"X\",\"subject\":\"S\",\"body\":\"B\"}")).andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ règle 12

    @Test
    void enrollmentCreatesFourTasksAndCannotBeDuplicated() throws Exception {
        UUID sequence = sequence();
        as(other, post("/api/sequences/" + sequence + "/enroll").contentType(MediaType.APPLICATION_JSON)
                .content("{\"contactId\":\"" + contact + "\"}")).andExpect(status().isForbidden());
        enroll(sequence).andExpect(status().isCreated());
        List<Map<String, Object>> tasks = jdbc.queryForList(
                "select title, due_date, owner_id from tasks where contact_id = ? order by due_date", contact);
        assertThat(tasks).hasSize(4);
        assertThat(tasks).extracting(t -> ((java.sql.Date) t.get("due_date")).toLocalDate())
                .containsExactly(today, today.plusDays(2), today.plusDays(4), today.plusDays(6));
        assertThat(tasks.get(0).get("title")).isEqualTo("E-mail : envoi de cotation · Amina Said");
        assertThat(tasks.get(2).get("title")).isEqualTo("Appel : point sur la décision · Amina Said");
        assertThat(tasks.get(3).get("title")).isEqualTo("Clôturer ou reprogrammer · Amina Said");
        assertThat(tasks).allMatch(t -> sales.getId().equals(t.get("owner_id")));
        enroll(sequence).andExpect(status().isConflict());
    }

    @Test
    void autoSendIsOffByDefaultAndSendsEachDueStepOnce() throws Exception {
        UUID sequence = sequence();
        enroll(sequence).andExpect(status().isCreated());
        sequenceService.runDueSteps();
        verify(mail, never()).send(any(MimeMessage.class));

        // Réservé aux managers et administrateurs
        as(sales, patch("/api/sequences/" + sequence).contentType(MediaType.APPLICATION_JSON).content("{\"autoSend\":true}"))
                .andExpect(status().isForbidden());
        jdbc.update("update sequences set auto_send = true where id = ?", sequence);
        try {
            sequenceService.runDueSteps();
            sequenceService.runDueSteps();
            var captor = ArgumentCaptor.forClass(MimeMessage.class);
            verify(mail, atLeastOnce()).send(captor.capture());
            assertThat(captor.getAllValues().stream().filter(m -> {
                try {
                    return m.getAllRecipients()[0].toString().equals("amina." + tag + "@ship.example");
                } catch (Exception e) {
                    return false;
                }
            })).hasSize(1);
            assertThat(jdbc.queryForObject("""
                    select done from tasks where contact_id = ? and title like 'E-mail : envoi%'""", Boolean.class, contact)).isTrue();
            assertThat(jdbc.queryForObject("""
                    select done from tasks where contact_id = ? and title like 'E-mail : relance%'""", Boolean.class, contact)).isFalse();
        } finally {
            jdbc.update("update sequences set auto_send = false where id = ?", sequence);
        }
    }

    // ------------------------------------------------------------------ helpers

    private User user(String first, String last) {
        User u = new User(first.toLowerCase() + "." + tag + "@quotes.test", "x", first, last);
        u.setRole(Role.SALES);
        u.markEmailVerified(Instant.now(), false);
        u.setStatus(UserStatus.ACTIVE);
        return users.save(u);
    }

    private ResultActions as(User u, MockHttpServletRequestBuilder req) throws Exception {
        return mvc.perform(req.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.issue(u).value()));
    }

    private UUID id(ResultActions created) throws Exception {
        String body = created.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(json.readTree(body).get("id").asText());
    }

    private UUID quote(Integer price, Integer barge) throws Exception {
        String extra = (price == null ? "" : ",\"priceUsdPerMt\":" + price) + (barge == null ? "" : ",\"bargeFeeUsd\":" + barge);
        return id(as(sales, post("/api/quotes").contentType(MediaType.APPLICATION_JSON)
                .content("{\"dealId\":\"" + deal + "\"" + extra + "}")));
    }

    private ResultActions send(UUID quote, String subject, String body) throws Exception {
        return as(sales, post("/api/quotes/" + quote + "/send").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("subject", subject, "body", body))));
    }

    private ResultActions enroll(UUID sequence) throws Exception {
        return as(sales, post("/api/sequences/" + sequence + "/enroll").contentType(MediaType.APPLICATION_JSON)
                .content("{\"contactId\":\"" + contact + "\"}"));
    }

    private UUID sequence() {
        return jdbc.queryForObject("select id from sequences where name = 'Relance cotation'", UUID.class);
    }

    private void moveDeal(String stage) throws Exception {
        as(sales, patch("/api/deals/" + deal + "/stage").contentType(MediaType.APPLICATION_JSON)
                .content("{\"stage\":\"" + stage + "\"}")).andExpect(status().isNoContent());
    }

    private String number(UUID quote) {
        return jdbc.queryForObject("select number from quotes where id = ?", String.class, quote);
    }

    private String quoteStatus(UUID quote) {
        return jdbc.queryForObject("select status from quotes where id = ?", String.class, quote);
    }

    private String dealStage() {
        return jdbc.queryForObject("select stage from deals where id = ?", String.class, deal);
    }

    private static void collect(Part part, List<Part> out) throws Exception {
        Object content = part.getContent();
        if (content instanceof Multipart mp) {
            for (int i = 0; i < mp.getCount(); i++) {
                collect(mp.getBodyPart(i), out);
            }
        } else {
            out.add(part);
        }
    }

    private static String text(Part p) {
        try {
            return p.getContent() instanceof String s ? s : "";
        } catch (Exception e) {
            return "";
        }
    }
}
