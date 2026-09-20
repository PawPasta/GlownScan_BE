package com.pawpasta.glowscan_be.implement;

import com.pawpasta.glowscan_be.config.JwtConfig;
import com.pawpasta.glowscan_be.modal.entity.User;
import com.pawpasta.glowscan_be.service.TokenService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

@Service
public class TokenServiceImpl implements TokenService {

    private final JwtEncoder jwtEncoder;
    private final JwtConfig jwtConfig;
    private final JwtDecoder jwtDecoder;
    private final JwtDecoder refreshJwtDecoder;
    private final SecureRandom secureRandom = new SecureRandom();

    public TokenServiceImpl(
            JwtEncoder jwtEncoder,
            JwtConfig jwtConfig,
            @Qualifier("jwtDecoder") JwtDecoder jwtDecoder,
            @Qualifier("refreshJwtDecoder") JwtDecoder refreshJwtDecoder
    ) {
        this.jwtEncoder = jwtEncoder;
        this.jwtConfig = jwtConfig;
        this.jwtDecoder = jwtDecoder;
        this.refreshJwtDecoder = refreshJwtDecoder;
    }

    @Override
    public String generateActionToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    @Override
    public String hashActionToken(String rawToken) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    @Override
    public String generateJWTToken(User user) {
        Instant now = Instant.now();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(jwtConfig.getIssuer())
                .audience(List.of(jwtConfig.getAudience()))
                .subject(user.getEmail())
                .issuedAt(now)
                .expiresAt(now.plus(jwtConfig.getAccessTokenValidity()))
                .claim("uid", user.getId().toString())
                .claim("token_version", user.getTokenVersion() == null ? 1 : user.getTokenVersion())
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    @Override
    public Jwt decodeJWTToken(String token) {
        return jwtDecoder.decode(token);
    }

    @Override
    public Jwt decodeJWTTokenForRefresh(String token) {
        return refreshJwtDecoder.decode(token);
    }
}
