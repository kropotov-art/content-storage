package ru.kropotov.storage.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;


@Configuration
public class SwaggerConfig {

    @Profile({"default", "dev", "local", "test"})
    @Configuration
    @SecurityScheme(
            name = "xUserId",
            type = SecuritySchemeType.APIKEY,
            in = SecuritySchemeIn.HEADER,
            paramName = "X-User-Id")
    @OpenAPIDefinition(
            info = @Info(title = "Storage API", version = "v1-dev"),
            security = @SecurityRequirement(name = "xUserId"))
    public static class DevSwaggerSecurityConfig {
        /* only annotations – no code required */
    }

    /** Swagger + OpenAPI for production: Bearer JWT */
    @Profile("production")
    @Configuration
    @SecurityScheme(
            name = "bearerAuth",
            type = SecuritySchemeType.HTTP,
            scheme = "bearer",
            bearerFormat = "JWT")
    @OpenAPIDefinition(
            info = @Info(title = "Storage API", version = "v1"),
            security = @SecurityRequirement(name = "bearerAuth"))
    public class ProdSwaggerSecurityConfig {
        /* only annotations – no code required */
    }


}
