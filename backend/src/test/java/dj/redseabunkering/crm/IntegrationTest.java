package dj.redseabunkering.crm;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/** Base des tests d'intégration : un seul PostgreSQL partagé par toute la suite. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "app.auth.jwt-secret=test-secret-test-secret-test-secret-0123456789",
        "app.auth.cookie-secure=false",
        "app.auth.admin-email=admin@test.rsb",
        "app.auth.admin-password=AdminPass2026",
        "app.signup-requires-approval=true",
})
public abstract class IntegrationTest {

    @ServiceConnection
    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }
}
