package com.pawpasta.glowscan_be.util;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

public final class JwtUtil {

    private static final int HS256_MINIMUM_KEY_LENGTH_BYTES = 32;

    private JwtUtil() {
    }

    public static SecretKey decodeHmacSha256Secret(String base64Secret) {
        if (base64Secret == null || base64Secret.isBlank()) {
            throw new IllegalArgumentException("JWT secret is required");
        }

        final byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(base64Secret);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("JWT secret must be Base64 encoded", exception);
        }

        if (keyBytes.length < HS256_MINIMUM_KEY_LENGTH_BYTES) {
            throw new IllegalArgumentException("JWT secret must be at least 256 bits for HS256");
        }

        return new SecretKeySpec(keyBytes, "HmacSHA256");
    }
}
