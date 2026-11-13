package com.minibank.common.config;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Reads a {@code DATABASE_URL} such as {@code postgresql://user:password@host:5432/name}, the form
 * Render gives, into the {@code DB_URL}, {@code DB_USERNAME} and {@code DB_PASSWORD} the profiles use.
 *
 * <p>Variables that are set explicitly win, and nothing happens when {@code DATABASE_URL} is absent.
 */
public class DatabaseUrlEnvironmentPostProcessor implements EnvironmentPostProcessor {

    static final String SOURCE_NAME = "databaseUrl";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String databaseUrl = environment.getProperty("DATABASE_URL");
        if (databaseUrl == null || databaseUrl.isBlank()) {
            return;
        }
        Map<String, Object> translated = translate(databaseUrl, environment);
        environment.getPropertySources().addLast(new MapPropertySource(SOURCE_NAME, translated));
    }

    static Map<String, Object> translate(String databaseUrl, ConfigurableEnvironment environment) {
        URI uri = URI.create(databaseUrl.trim());
        String scheme = uri.getScheme();
        if (!"postgresql".equals(scheme) && !"postgres".equals(scheme)) {
            throw new IllegalStateException("DATABASE_URL must start with postgresql:// or postgres://");
        }
        if (uri.getHost() == null || uri.getRawPath() == null || uri.getRawPath().length() < 2) {
            throw new IllegalStateException("DATABASE_URL must name a host and a database");
        }

        Map<String, Object> properties = new HashMap<>();
        if (!environment.containsProperty("DB_URL")) {
            String port = uri.getPort() == -1 ? "" : ":" + uri.getPort();
            String query = uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery();
            properties.put("DB_URL", "jdbc:postgresql://" + uri.getHost() + port + uri.getRawPath() + query);
        }

        String userInfo = uri.getRawUserInfo();
        if (userInfo != null) {
            int colon = userInfo.indexOf(':');
            String user = colon == -1 ? userInfo : userInfo.substring(0, colon);
            if (!environment.containsProperty("DB_USERNAME")) {
                properties.put("DB_USERNAME", decode(user));
            }
            if (colon != -1 && !environment.containsProperty("DB_PASSWORD")) {
                properties.put("DB_PASSWORD", decode(userInfo.substring(colon + 1)));
            }
        }
        return properties;
    }

    // Percent-decoding only: in a URL's user info a '+' is a plus, not a space.
    private static String decode(String value) {
        return URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8);
    }
}
