package com.minibank;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

// Authentication is entirely token based, so Spring Boot's default in-memory user is excluded:
// it would otherwise be created (and its generated password logged) for no reason.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
@EnableJpaAuditing
@EnableScheduling
public class MiniBankApplication {

    public static void main(String[] args) {
        SpringApplication.run(MiniBankApplication.class, args);
    }
}
