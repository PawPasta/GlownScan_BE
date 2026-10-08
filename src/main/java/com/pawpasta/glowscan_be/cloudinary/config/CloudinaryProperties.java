package com.pawpasta.glowscan_be.cloudinary.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.Set;

@Validated
@ConfigurationProperties(prefix = "app.cloudinary")
public record CloudinaryProperties(
        @NotBlank String cloudName,
        @NotBlank String apiKey,
        @NotBlank String apiSecret,
        @NotNull @Valid Upload avatar,
        @NotNull @Valid Upload analysis
) {

    public record Upload(
            @NotBlank String folder,
            @NotEmpty Set<@Pattern(regexp = "(?i)jpg|jpeg|png|webp") String> allowedFormats,
            @Positive long maxBytes,
            @Positive int maxDimension,
            @NotNull Duration uploadIntentTtl
    ) {
    }
}
