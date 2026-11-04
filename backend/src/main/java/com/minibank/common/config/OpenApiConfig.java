package com.minibank.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** OpenAPI document metadata and the bearer authentication scheme used by every secured endpoint. */
@Configuration
public class OpenApiConfig {

    public static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI mbankOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("MBank API")
                        .version("v1")
                        .description(
                                """
                                Modular banking platform API.

                                ## Cards
                                `/api/v1/cards` issues and manages cards. Card numbers are generated
                                locally and encrypted at rest; no verification value is stored anywhere,
                                it is derived on demand. The values are simulated and authorise nothing.

                                ## Authentication
                                Call `POST /api/v1/auth/login` to obtain an access token and a refresh token,
                                then send the access token as `Authorization: Bearer <token>` on every other
                                request. Access tokens are short lived; use `POST /api/v1/auth/refresh` to
                                rotate them.

                                ## Idempotency
                                `POST /api/v1/transfers` requires an `Idempotency-Key` header. Replaying the
                                same key with the same body returns the original result instead of moving
                                money twice; replaying it with a different body is rejected with
                                `IDEMPOTENCY_KEY_CONFLICT`.

                                ## Errors
                                Every failure returns the same `ApiError` body carrying a stable machine
                                readable `code`, the correlation id of the request, and field level details
                                for validation failures.
                                """)
                        .contact(new Contact().name("MBank"))
                        .license(new License().name("MIT").url("https://opensource.org/licenses/MIT")))
                .servers(List.of(new Server().url("/").description("Current host")))
                .components(new Components()
                        .addSecuritySchemes(
                                BEARER_SCHEME,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Access token issued by /api/v1/auth/login")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
