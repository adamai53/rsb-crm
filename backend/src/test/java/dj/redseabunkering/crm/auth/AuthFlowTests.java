package dj.redseabunkering.crm.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import dj.redseabunkering.crm.IntegrationTest;
import dj.redseabunkering.crm.user.Role;
import dj.redseabunkering.crm.user.User;
import dj.redseabunkering.crm.user.UserRepository;
import dj.redseabunkering.crm.user.UserStatus;

/** Parcours complet : inscription → vérification → validation → login → refresh → logout, et protections. */
@AutoConfigureMockMvc
class AuthFlowTests extends IntegrationTest {

    private static final String PASSWORD = "Soutage2026";

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    UserRepository users;

    @Autowired
    PasswordEncoder passwordEncoder;

    @MockitoBean
    AuthMailer mailer;

    @Test
    void fullSignupLifecycle() throws Exception {
        String email = unique("fatouma");

        // Inscription → compte PENDING, lien de vérification envoyé
        postJson("/api/auth/register", """
                {"firstName":"Fatouma","lastName":"Ali","email":"%s","password":"%s"}""".formatted(email, PASSWORD))
                .andExpect(status().isAccepted());
        assertThat(user(email).getStatus()).isEqualTo(UserStatus.PENDING);
        assertThat(user(email).getInitials()).isEqualTo("FA");

        // Connexion refusée tant que l'e-mail n'est pas vérifié
        login(email, PASSWORD).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("pas encore vérifiée")));

        // Vérification → en attente de validation admin ; le lien ne sert qu'une fois
        String verification = capturedVerificationToken();
        postJson("/api/auth/verify-email", tokenBody(verification)).andExpect(status().isOk());
        assertThat(user(email).getStatus()).isEqualTo(UserStatus.PENDING_APPROVAL);
        postJson("/api/auth/verify-email", tokenBody(verification)).andExpect(status().isBadRequest());
        verify(mailer).sendApprovalRequest(any(), argThat(u -> u.getEmail().equals(email)));
        login(email, PASSWORD).andExpect(status().isForbidden());

        // Validation par l'admin
        String adminToken = accessToken(login("admin@test.rsb", "AdminPass2026").andExpect(status().isOk()).andReturn());
        mvc.perform(post("/api/users/" + user(email).getId() + "/approve").header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        // Connexion → access token + cookie refresh HttpOnly SameSite=Strict
        MvcResult loginResult = login(email, PASSWORD).andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("SALES"))
                .andReturn();
        String setCookie = loginResult.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).contains("HttpOnly").contains("SameSite=Strict").contains("Path=/api/auth");
        String access = accessToken(loginResult);
        Cookie refresh1 = refreshCookie(loginResult);

        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(access)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        // Rotation : refresh1 → refresh2
        MvcResult refreshed = mvc.perform(post("/api/auth/refresh").cookie(refresh1)).andExpect(status().isOk()).andReturn();
        Cookie refresh2 = refreshCookie(refreshed);
        assertThat(refresh2.getValue()).isNotEqualTo(refresh1.getValue());

        // Réutilisation de refresh1 (vol supposé) → toute la famille est révoquée, refresh2 compris
        mvc.perform(post("/api/auth/refresh").cookie(refresh1)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/refresh").cookie(refresh2)).andExpect(status().isUnauthorized());

        // Nouvelle session puis déconnexion : le refresh ne marche plus
        Cookie refresh3 = refreshCookie(login(email, PASSWORD).andExpect(status().isOk()).andReturn());
        mvc.perform(post("/api/auth/logout").cookie(refresh3)).andExpect(status().isNoContent());
        mvc.perform(post("/api/auth/refresh").cookie(refresh3)).andExpect(status().isUnauthorized());
    }

    @Test
    void weakPasswordIsRejectedWithFieldError() throws Exception {
        postJson("/api/auth/register", """
                {"firstName":"Ibrahim","lastName":"Omar","email":"%s","password":"court"}""".formatted(unique("io")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").value(org.hamcrest.Matchers.containsString("10 caractères")));
    }

    @Test
    void registeringAnExistingEmailDoesNotRevealIt() throws Exception {
        User existing = activeUser(unique("exist"), Role.SALES);
        postJson("/api/auth/register", """
                {"firstName":"X","lastName":"Y","email":"%s","password":"%s"}""".formatted(existing.getEmail(), PASSWORD))
                .andExpect(status().isAccepted());
        verify(mailer).sendAccountAlreadyExists(argThat(u -> u.getEmail().equals(existing.getEmail())));
    }

    @Test
    void unknownEmailAndWrongPasswordGiveTheSameMessage() throws Exception {
        User u = activeUser(unique("same"), Role.SALES);
        String unknown = login(unique("nobody"), PASSWORD).andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        String wrong = login(u.getEmail(), "Mauvais2026x").andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(unknown).get("detail")).isEqualTo(json.readTree(wrong).get("detail"));
    }

    @Test
    void accountIsLockedAfterFiveFailures() throws Exception {
        User u = activeUser(unique("lock"), Role.SALES);
        for (int i = 0; i < User.MAX_FAILED_LOGINS; i++) {
            login(u.getEmail(), "Mauvais2026x").andExpect(status().isUnauthorized());
        }
        // Même le bon mot de passe est refusé pendant le blocage
        login(u.getEmail(), PASSWORD).andExpect(status().isTooManyRequests());
    }

    @Test
    void passwordResetWorksOnceAndClosesSessions() throws Exception {
        User u = activeUser(unique("reset"), Role.SALES);
        Cookie oldSession = refreshCookie(login(u.getEmail(), PASSWORD).andExpect(status().isOk()).andReturn());

        postJson("/api/auth/forgot-password", "{\"email\":\"" + u.getEmail() + "\"}").andExpect(status().isAccepted());
        var captor = ArgumentCaptor.forClass(String.class);
        verify(mailer).sendPasswordReset(any(), captor.capture());

        String body = "{\"token\":\"" + captor.getValue() + "\",\"password\":\"NouveauMdp2026\"}";
        postJson("/api/auth/reset-password", body).andExpect(status().isOk());
        postJson("/api/auth/reset-password", body).andExpect(status().isBadRequest());

        mvc.perform(post("/api/auth/refresh").cookie(oldSession)).andExpect(status().isUnauthorized());
        login(u.getEmail(), PASSWORD).andExpect(status().isUnauthorized());
        login(u.getEmail(), "NouveauMdp2026").andExpect(status().isOk());
    }

    @Test
    void forgotPasswordForUnknownEmailLooksTheSame() throws Exception {
        postJson("/api/auth/forgot-password", "{\"email\":\"" + unique("ghost") + "\"}").andExpect(status().isAccepted());
    }

    @Test
    void salesCannotUseAdministration() throws Exception {
        User sales = activeUser(unique("sales"), Role.SALES);
        String token = accessToken(login(sales.getEmail(), PASSWORD).andExpect(status().isOk()).andReturn());
        mvc.perform(get("/api/users").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/users/" + sales.getId() + "/role").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanChangeRoleAndDisableButNotThemselves() throws Exception {
        User target = activeUser(unique("target"), Role.SALES);
        MvcResult adminLogin = login("admin@test.rsb", "AdminPass2026").andExpect(status().isOk()).andReturn();
        String admin = accessToken(adminLogin);
        String adminId = json.readTree(adminLogin.getResponse().getContentAsString()).at("/user/id").asText();

        mvc.perform(patch("/api/users/" + target.getId() + "/role").header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"MANAGER\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("MANAGER"));
        mvc.perform(post("/api/users/" + target.getId() + "/disable").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DISABLED"));
        login(target.getEmail(), PASSWORD).andExpect(status().isForbidden());

        mvc.perform(post("/api/users/" + adminId + "/disable").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isConflict());
    }

    @Test
    void profileCanBeUpdatedAndPasswordChanged() throws Exception {
        User u = activeUser(unique("profile"), Role.SALES);
        MvcResult res = login(u.getEmail(), PASSWORD).andExpect(status().isOk()).andReturn();
        String token = accessToken(res);
        Cookie session = refreshCookie(res);

        mvc.perform(patch("/api/users/me").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Hassan\",\"lastName\":\"Doualeh\",\"language\":\"en\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.initials").value("HD"))
                .andExpect(jsonPath("$.language").value("en"));

        mvc.perform(post("/api/auth/password").header(HttpHeaders.AUTHORIZATION, bearer(token)).cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"faux\",\"newPassword\":\"Changement2026\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/password").header(HttpHeaders.AUTHORIZATION, bearer(token)).cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"Changement2026\"}"))
                .andExpect(status().isNoContent());
        // La session courante reste valide
        mvc.perform(post("/api/auth/refresh").cookie(session)).andExpect(status().isOk());
        login(u.getEmail(), "Changement2026").andExpect(status().isOk());
    }

    @Test
    void invalidTokenIsRejected() throws Exception {
        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ helpers

    private ResultActions postJson(String url, String body) throws Exception {
        return mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions login(String email, String password) throws Exception {
        return postJson("/api/auth/login", "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}");
    }

    private String accessToken(MvcResult result) throws Exception {
        JsonNode body = json.readTree(result.getResponse().getContentAsString());
        return body.get("accessToken").asText();
    }

    private static Cookie refreshCookie(MvcResult result) {
        String header = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(header).startsWith(RefreshCookie.NAME + "=");
        String value = header.substring(RefreshCookie.NAME.length() + 1, header.indexOf(';'));
        return new Cookie(RefreshCookie.NAME, value);
    }

    private String capturedVerificationToken() {
        var captor = ArgumentCaptor.forClass(String.class);
        verify(mailer).sendEmailVerification(any(), captor.capture());
        return captor.getValue();
    }

    private static String tokenBody(String token) {
        return "{\"token\":\"" + token + "\"}";
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8) + "@test.rsb";
    }

    private User user(String email) {
        return users.findByEmail(email).orElseThrow();
    }

    private User activeUser(String email, Role role) {
        User u = new User(email, passwordEncoder.encode(PASSWORD), "Test", "User");
        u.setRole(role);
        u.markEmailVerified(java.time.Instant.now(), false);
        u.setStatus(UserStatus.ACTIVE);
        return users.save(u);
    }
}
