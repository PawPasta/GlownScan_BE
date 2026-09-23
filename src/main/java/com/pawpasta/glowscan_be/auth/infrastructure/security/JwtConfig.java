package com.pawpasta.glowscan_be.auth.infrastructure.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import com.pawpasta.glowscan_be.auth.domain.enums.UserStatus;
import com.pawpasta.glowscan_be.auth.infrastructure.repository.UserRepository;
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
import java.util.UUID;


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

        return new SecretKeySpec(keyBytes, "HmacSHAH256");
    }


    @Bean
    public JwtEncoder jwtEncoder(SecretKey secretKey) {
        return new NimbusJwtEncoder(
                new ImmutableSecret<SecurityContext>(secretKey)
        );
    }

    @Bean
    @Primary
    public JwtDecoder jwtDecoder(SecretKey secretKey, UserRepository userRepository) {

        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS256).build();

        OAuth2TokenValidator<Jwt> audienceValidator = jwt -> {
            if (Objects.requireNonNull(jwt.getAudience()).contains(audience)) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(
                    new OAuth2Error(
                            "invalid_token",
                            "Token Audience is not valid",
                            null
                    )
                );
        };

        decoder.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(
                        JwtValidators.createDefaultWithIssuer(issuer),
                        audienceValidator,
                        tokenVersionValidator(userRepository)
                )
        );

        return decoder;
    }

    private OAuth2TokenValidator<Jwt> tokenVersionValidator(UserRepository userRepository) {
        return jwt -> {
            String userIdClaim = jwt.getClaimAsString("uid");
            Object tokenVersionClaim = jwt.getClaim("token_version");

            if (userIdClaim == null || userIdClaim.isBlank() || !(tokenVersionClaim instanceof Number version)) {
                return invalidTokenVersionResult();
            }

            try {
                int tokenVersion = version.intValue();
                if (tokenVersion < 1) {
                    return invalidTokenVersionResult();
                }

                boolean tokenIsCurrent = userRepository.findById(UUID.fromString(userIdClaim))
                        .filter(user -> user.getDeletedAt() == null)
                        .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                        .map(user -> user.getTokenVersion() != null && user.getTokenVersion() == tokenVersion)
                        .orElse(false);

                return tokenIsCurrent
                        ? OAuth2TokenValidatorResult.success()
                        : invalidTokenVersionResult();
            } catch (IllegalArgumentException exception) {
                return invalidTokenVersionResult();
            }
        };
    }

    private OAuth2TokenValidatorResult invalidTokenVersionResult() {
        return OAuth2TokenValidatorResult.failure(
                new OAuth2Error("invalid_token", "Token is no longer valid", null)
        );
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
