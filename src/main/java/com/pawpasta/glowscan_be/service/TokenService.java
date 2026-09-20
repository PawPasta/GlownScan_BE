package com.pawpasta.glowscan_be.service;

import com.pawpasta.glowscan_be.modal.entity.User;
import org.springframework.security.oauth2.jwt.Jwt;

public interface TokenService {

    String generateActionToken();

    String hashActionToken(String token);

    String generateJWTToken(User user);

    Jwt decodeJWTToken(String token);

    Jwt decodeJWTTokenForRefresh(String token);
}
