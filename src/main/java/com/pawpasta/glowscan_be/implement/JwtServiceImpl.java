package com.pawpasta.glowscan_be.implement;

import com.pawpasta.glowscan_be.config.JwtConfig;
import com.pawpasta.glowscan_be.modal.entity.User;
import com.pawpasta.glowscan_be.repository.UserRepository;
import com.pawpasta.glowscan_be.service.JwtService;
import lombok.AllArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;

import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@AllArgsConstructor
public class JwtServiceImpl implements JwtService {

    private final JwtEncoder jwtEncoder;
    private final JwtConfig jwtConfig;
    @Override
    public String generateToken(User user) {

        Instant now = Instant.now();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(jwtConfig.getIssuer())
                .audience(List.of(jwtConfig.getAudience()))
                .subject(user.getEmail())
                .issuedAt(now)
                .expiresAt(now.plus(jwtConfig.getAccessTokenValidity()))
                .id(String.valueOf(user.getId()))
                .build();

        JwsHeader header = JwsHeader
                .with(MacAlgorithm.HS256)
                .build();

        return jwtEncoder.encode(
                JwtEncoderParameters.from(header, claims)
        ).getTokenValue();
    }
}
