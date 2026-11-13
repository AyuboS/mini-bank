package com.minibank.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class DatabaseUrlEnvironmentPostProcessorTest {

    private final DatabaseUrlEnvironmentPostProcessor processor = new DatabaseUrlEnvironmentPostProcessor();

    private MockEnvironment apply(MockEnvironment environment) {
        processor.postProcessEnvironment(environment, null);
        return environment;
    }

    @Test
    void readsRendersInternalUrlWithoutAPort() {
        var environment = apply(new MockEnvironment()
                .withProperty("DATABASE_URL", "postgresql://minibank:s3cr%40t+1@dpg-abc123-a/minibank"));

        assertThat(environment.getProperty("DB_URL")).isEqualTo("jdbc:postgresql://dpg-abc123-a/minibank");
        assertThat(environment.getProperty("DB_USERNAME")).isEqualTo("minibank");
        assertThat(environment.getProperty("DB_PASSWORD")).isEqualTo("s3cr@t+1");
    }

    @Test
    void keepsThePortAndQuery() {
        var environment = apply(new MockEnvironment()
                .withProperty("DATABASE_URL", "postgres://u:p@db.example.com:6543/bank?sslmode=require"));

        assertThat(environment.getProperty("DB_URL"))
                .isEqualTo("jdbc:postgresql://db.example.com:6543/bank?sslmode=require");
    }

    @Test
    void explicitSettingsWin() {
        var environment = apply(new MockEnvironment()
                .withProperty("DATABASE_URL", "postgresql://u:p@host/db")
                .withProperty("DB_URL", "jdbc:postgresql://elsewhere/db")
                .withProperty("DB_PASSWORD", "explicit"));

        assertThat(environment.getProperty("DB_URL")).isEqualTo("jdbc:postgresql://elsewhere/db");
        assertThat(environment.getProperty("DB_USERNAME")).isEqualTo("u");
        assertThat(environment.getProperty("DB_PASSWORD")).isEqualTo("explicit");
    }

    @Test
    void doesNothingWithoutADatabaseUrl() {
        var environment = apply(new MockEnvironment());

        assertThat(environment.getPropertySources().contains(DatabaseUrlEnvironmentPostProcessor.SOURCE_NAME))
                .isFalse();
    }

    @Test
    void refusesAnUrlThatIsNotPostgres() {
        assertThatThrownBy(() -> apply(new MockEnvironment().withProperty("DATABASE_URL", "mysql://u:p@host/db")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("postgresql://");
    }
}
