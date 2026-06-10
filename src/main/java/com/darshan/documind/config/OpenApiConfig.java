package com.darshan.documind.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** API metadata for the Swagger UI served at /swagger-ui.html (springdoc). */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI documindOpenApi() {
        return new OpenAPI().info(new Info()
                .title("DocuMind API")
                .description("RAG document Q&A: upload PDFs, ask questions, get answers with citations.")
                .version("1.0.0"));
    }
}
