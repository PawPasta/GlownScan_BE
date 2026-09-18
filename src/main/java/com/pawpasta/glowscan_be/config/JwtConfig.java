package com.pawpasta.glowscan_be.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import com.pawpasta.glowscan_be.util.JwtUtil;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import javax.crypto.SecretKey;
import java.time.Duration;
import java.util.Objects;


@Configuration
@Getter
public class JwtConfig {

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
        return JwtUtil.decodeHmacSha256Secret(secret);
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKey secretKey) {
        return new NimbusJwtEncoder(
                new ImmutableSecret<SecurityContext>(secretKey)
        );
    }

    @Bean
    public JwtDecoder jwtDecoder(SecretKey secretKey) {

        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS256).build();

        OAuth2TokenValidator<Jwt> audienceValidator = jwt -> {
            if(Objects.requireNonNull(jwt.getAudience()).contains(audience)){
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
                        JwtValidators.createDefaultWithIssuer(issuer), audienceValidator
                )
        );

        return decoder;
    }

}
