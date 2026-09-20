package com.pawpasta.glowscan_be.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.Objects;


@Configuration
@Getter
public class JwtConfig {

    private static final int HS256_MINIMUM_KEY_LENGTH_BYTES = 32;

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.issuer}")
    private String issuer;

    @Value("${app.jwt.audience}")
    private String audience;

    @Value("${app.jwt.access-token-ttl}")
    Duration accessTokenValidity;

    @Bean
    public SecretKey jwtSecretKey() {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("JWT secret is required");
        }

        final byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("JWT secret must be Base64 encoded", exception);
        }

        if (keyBytes.length < HS256_MINIMUM_KEY_LENGTH_BYTES) {
            throw new IllegalArgumentException("JWT secret must be at least 256 bits for HS256");
        }

        return new SecretKeySpec(keyBytes, "HmacSHA256");
    }


    @Bean
    public JwtEncoder jwtEncoder(SecretKey secretKey) {
        return new NimbusJwtEncoder(
                new ImmutableSecret<SecurityContext>(secretKey)
        );
    }

    @Bean
    @Primary
    public JwtDecoder jwtDecoder(SecretKey secretKey) {

        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS256).build();

        OAuth2TokenValidator<Jwt> audienceValidator = jwt -> {
            if (Objects.requireNonNull(jwt.getAudience()).contains(audience)) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(
                    new OAuth2Error(
                            "invalid_token",
                            "Token Audiance is not valid",
                            null
                    )
                );
        };

        decoder.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(
                        JwtValidators.createDefaultWithIssuer(issuer),
                        audienceValidator
                )
        );

        return decoder;
    }

    @Bean
    public JwtDecoder refreshJwtDecoder(SecretKey secretKey) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS256).build();

        OAuth2TokenValidator<Jwt> issuerValidator = jwt -> {
            if (issuer.equals(jwt.getClaimAsString("iss"))) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(
                    new OAuth2Error("invalid_token", "Token issuer is not valid", null)
            );
        };

        OAuth2TokenValidator<Jwt> audienceValidator = jwt -> {
            if (Objects.requireNonNull(jwt.getAudience()).contains(audience)) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(
                    new OAuth2Error("invalid_token", "Token audience is not valid", null)
            );
        };

        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(issuerValidator, audienceValidator));
        return decoder;
    }

}
