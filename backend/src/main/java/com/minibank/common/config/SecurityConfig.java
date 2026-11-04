package com.minibank.common.config;

import com.minibank.common.ratelimit.RateLimitFilter;
import com.minibank.common.security.JwtAuthenticationFilter;
import com.minibank.common.security.RestAuthenticationEntryPoint;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * HTTP security: who may call what, and under which protections.
 *
 * <p>The API is stateless and token based, so there is no session and no CSRF token to protect: an
 * attacker's page cannot make a browser attach an {@code Authorization} header it does not have.
 * CSRF protection is therefore disabled deliberately, not by omission. This reasoning holds only
 * because the access token is never stored in a cookie.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_GET_ENDPOINTS = {
        "/actuator/health", "/actuator/health/**", "/actuator/info",
        "/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**"
    };

    private final SecurityProperties securityProperties;

    public SecurityConfig(SecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            RateLimitFilter rateLimitFilter,
            RestAuthenticationEntryPoint authenticationEntryPoint)
            throws Exception {

        http.csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(authenticationEntryPoint))
                .headers(headers -> headers
                        .contentTypeOptions(Customizer.withDefaults())
                        .frameOptions(HeadersConfigurer.FrameOptionsConfig::deny)
                        .referrerPolicy(referrer -> referrer.policy(
                                org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter
                                        .ReferrerPolicy.NO_REFERRER))
                        .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000))
                        // The API serves JSON only; a restrictive policy costs nothing and blocks
                        // any content that a future misconfiguration might render.
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'")))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, PUBLIC_GET_ENDPOINTS).permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(
                                "/api/v1/auth/register",
                                "/api/v1/auth/login",
                                "/api/v1/auth/refresh",
                                "/api/v1/auth/logout",
                                // Necessarily public: they exist to be called by somebody who has no
                                // session yet. Each is rate limited and none of them reveals whether
                                // an account exists.
                                "/api/v1/auth/phone/code",
                                "/api/v1/auth/phone/login",
                                "/api/v1/auth/phone/confirm",
                                "/api/v1/auth/email/verify",
                                // Password recovery: a customer who cannot sign in cannot present a
                                // token. Each is rate limited and none reveals whether an account
                                // exists -- see PasswordResetService.
                                "/api/v1/security/password-reset/request",
                                "/api/v1/security/password-reset/verify",
                                "/api/v1/security/password-reset/complete")
                        .permitAll()
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .requestMatchers("/actuator/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                // Rate limiting runs after authentication so that the bucket can be keyed by user.
                .addFilterAfter(rateLimitFilter, JwtAuthenticationFilter.class);

        return http.build();
    }

    /**
     * BCrypt at cost 12. The cost is deliberately above the Spring default of 10: it roughly
     * quadruples the work an offline cracker must do per guess, at a login cost of a few tens of
     * milliseconds.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(securityProperties.cors().allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(
                List.of("Authorization", "Content-Type", "Idempotency-Key", "X-Request-Id"));
        configuration.setExposedHeaders(List.of("X-Request-Id", "X-RateLimit-Limit", "X-RateLimit-Remaining"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);

        /*
         * The health probe gets its own, smaller grant.
         *
         * The web client asks the readiness endpoint whether the API is up, so that a browser can
         * say "the API is not running" instead of failing every request in isolation. Without a
         * CORS entry that probe is blocked whenever the app and the API are served from different
         * origins -- which is the normal Docker layout, :3000 to :8080 -- and the client is left
         * unable to tell a stopped server apart from a healthy one.
         *
         * Deliberately narrower than the API grant: GET only, no credentials, no custom headers.
         * The endpoint is already reachable without authentication, and readiness reveals nothing
         * beyond whether the application is accepting traffic.
         */
        CorsConfiguration health = new CorsConfiguration();
        health.setAllowedOrigins(securityProperties.cors().allowedOrigins());
        health.setAllowedMethods(List.of("GET", "OPTIONS"));
        health.setAllowedHeaders(List.of("Content-Type"));
        health.setAllowCredentials(false);
        health.setMaxAge(3600L);
        source.registerCorsConfiguration("/actuator/health/**", health);

        return source;
    }
}
