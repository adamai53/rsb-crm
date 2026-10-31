package dj.redseabunkering.crm;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base des tests d'intégration : un seul PostgreSQL partagé par toute la suite. Testcontainers par défaut ;
 * sans Docker, TEST_DB_URL (+ TEST_DB_USER, TEST_DB_PASSWORD) désigne une base vide à utiliser à la place.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "app.auth.jwt-secret=test-secret-test-secret-test-secret-0123456789",
        "app.auth.cookie-secure=false",
        "app.auth.admin-email=admin@test.rsb",
        "app.auth.admin-password=AdminPass2026",
        "app.signup-requires-approval=true",
})
public abstract class IntegrationTest {

    private static final String EXTERNAL_URL = System.getenv("TEST_DB_URL");
    private static final PostgreSQLContainer<?> POSTGRES =
            EXTERNAL_URL == null ? new PostgreSQLContainer<>("postgres:16-alpine") : null;

    static {
        if (POSTGRES != null) {
            POSTGRES.start();
        }
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        if (POSTGRES != null) {
            registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
            registry.add("spring.datasource.username", POSTGRES::getUsername);
            registry.add("spring.datasource.password", POSTGRES::getPassword);
        } else {
            registry.add("spring.datasource.url", () -> EXTERNAL_URL);
            registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("TEST_DB_USER", "postgres"));
            registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("TEST_DB_PASSWORD", ""));
        }
    }
}
