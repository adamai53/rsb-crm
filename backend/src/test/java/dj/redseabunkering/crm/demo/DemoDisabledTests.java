package dj.redseabunkering.crm.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import dj.redseabunkering.crm.IntegrationTest;
import dj.redseabunkering.crm.auth.AccessTokenService;
import dj.redseabunkering.crm.user.UserRepository;

/** Sans APP_SEED_DEMO : aucun chargement au démarrage, réinitialisation refusée même à un ADMIN. */
@AutoConfigureMockMvc
class DemoDisabledTests extends IntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ApplicationContext context;

    @Autowired
    UserRepository users;

    @Autowired
    AccessTokenService tokens;

    @Test
    void nothingRunsWithoutTheFlag() throws Exception {
        assertThat(context.getBeansOfType(DemoSeeder.class)).isEmpty();
        String token = tokens.issue(users.findByEmail("admin@test.rsb").orElseThrow()).value();
        mvc.perform(post("/api/admin/demo/reset").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isConflict());
    }
}
