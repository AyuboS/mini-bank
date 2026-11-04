package com.minibank.common.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** JSON conventions shared by the whole API. */
@Configuration
public class JacksonConfig {

    /**
     * Rejects request bodies containing unknown properties.
     *
     * <p>This is a mass-assignment guard as much as a strictness setting: a client that posts
     * {@code {"amount": 10, "role": "ADMIN"}} gets a 400 rather than having the stray field silently
     * ignored, which makes accidental binding of privileged fields visible during development.
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jsonCustomizer() {
        return builder -> builder.featuresToEnable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .featuresToDisable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}
