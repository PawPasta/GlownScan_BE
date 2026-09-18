package com.pawpasta.glowscan_be.service;

public interface OpaqueTokenService {

    String generateToken();
    String hashToken(String token);
}
