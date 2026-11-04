package com.minibank.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for tests that exercise the real stack against a real PostgreSQL.
 *
 * <p>The database is PostgreSQL, not H2, because the behaviour under test <em>is</em> PostgreSQL
 * behaviour: {@code SELECT ... FOR UPDATE} row locking, {@code NUMERIC(19,4)} arithmetic, partial
 * unique indexes, JSONB columns and check constraints. An in-memory substitute would either not
 * support these or emulate them differently, so a green suite would prove nothing about production.
 *
 * <p>The container is a singleton started once per JVM rather than per test class. Testcontainers'
 * Ryuk sidecar removes it when the JVM exits, so nothing is left running.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("minibank")
                    .withUsername("minibank")
                    .withPassword("minibank");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected DatabaseCleaner databaseCleaner;

    @Autowired
    protected TestDataFactory data;

    @Autowired
    protected ApiClient api;

    @Autowired
    protected com.minibank.common.ratelimit.TokenBucketRateLimiter rateLimiter;

    @Autowired
    protected RecordingEmailService emails;

    /**
     * Each test starts from an empty database.
     *
     * <p>Deliberately not {@code @Transactional} with rollback: much of what is under test depends
     * on real commit boundaries — {@code REQUIRES_NEW} audit records, idempotency reservations,
     * row locks taken by concurrent threads — none of which behave correctly inside a single
     * test-managed transaction that is rolled back at the end.
     */
    @BeforeEach
    void resetState() {
        databaseCleaner.reset();
        // The limiter is a singleton in the shared application context, so without this a test
        // would inherit whatever budget the previous one spent.
        rateLimiter.reset();
        emails.clear();
    }

    protected String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not serialise test payload", ex);
        }
    }
}
