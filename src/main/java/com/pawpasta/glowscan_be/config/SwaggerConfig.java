package com.pawpasta.glowscan_be.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    OpenAPI glowScanOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("GlowScan API")
                        .description("REST API documentation for the GlowScan backend.")
                        .version("v1"));
    }
}
