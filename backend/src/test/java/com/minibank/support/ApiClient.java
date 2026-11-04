package com.minibank.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Thin helper over MockMvc for the calls every test needs.
 *
 * <p>Tests authenticate through the real login endpoint rather than by injecting a mock security
 * context, so token issuing, parsing and the whole filter chain are exercised on the way in.
 *
 * <p>The request builders are fully qualified rather than statically imported: this class defines
 * its own {@code get}/{@code post}/{@code patch}/{@code delete}, and a static import of the same
 * names would be shadowed by them.
 */
@Component
public class ApiClient {

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;

    public ApiClient(MockMvc mockMvc, ObjectMapper objectMapper) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
    }

    // --- authentication ---

    /** Logs in and returns the access token. */
    public String login(String username, String password) {
        return loginResponse(username, password).get("accessToken").asText();
    }

    /** Logs in a user created by {@link TestDataFactory}, which shares one password. */
    public String loginAsTestUser(String username) {
        return login(username, TestDataFactory.PASSWORD);
    }

    public JsonNode loginResponse(String username, String password) {
        return parse(post("/api/v1/auth/login", null, new Credentials(username, password)));
    }

    /** Attempts a login and returns only the status, for tests about failure handling. */
    public int attemptLogin(String username, String password) {
        return post("/api/v1/auth/login", null, new Credentials(username, password))
                .getResponse()
                .getStatus();
    }

    // --- verbs ---

    public MvcResult get(String path, String token) {
        return execute(MockMvcRequestBuilders.get(path), token, null, Map.of());
    }

    /** Convenience for reads whose body is the interesting part. */
    public JsonNode getAsUser(String path, String token) {
        return parse(get(path, token));
    }

    public MvcResult post(String path, String token, Object body) {
        return post(path, token, body, Map.of());
    }

    /** POSTs with extra headers, for example an {@code Idempotency-Key}. */
    public MvcResult post(String path, String token, Object body, Map<String, String> headers) {
        return execute(MockMvcRequestBuilders.post(path), token, body, headers);
    }

    public MvcResult patch(String path, String token, Object body) {
        return execute(MockMvcRequestBuilders.patch(path), token, body, Map.of());
    }

    public MvcResult put(String path, String token, Object body) {
        return execute(MockMvcRequestBuilders.put(path), token, body, Map.of());
    }

    public MvcResult delete(String path, String token) {
        return execute(MockMvcRequestBuilders.delete(path), token, null, Map.of());
    }

    private MvcResult execute(
            MockHttpServletRequestBuilder builder, String token, Object body, Map<String, String> headers) {
        try {
            builder.contentType(MediaType.APPLICATION_JSON);
            if (token != null) {
                builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
            }
            if (body != null) {
                builder.content(body instanceof String raw ? raw : objectMapper.writeValueAsString(body));
            }
            headers.forEach(builder::header);
            return mockMvc.perform(builder).andReturn();
        } catch (Exception ex) {
            throw new IllegalStateException("Request failed", ex);
        }
    }

    public JsonNode parse(MvcResult result) {
        try {
            String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
            return body.isEmpty() ? objectMapper.createObjectNode() : objectMapper.readTree(body);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not parse response body", ex);
        }
    }

    public record Credentials(String username, String password) {}
}
