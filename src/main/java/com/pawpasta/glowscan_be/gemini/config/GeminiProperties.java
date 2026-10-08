package com.pawpasta.glowscan_be.gemini.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.gemini")
public record GeminiProperties(
        @NotBlank String apiKey,
        @NotBlank String model,
        @NotNull Duration timeout,
        @NotBlank String baseUrl
) {
}
