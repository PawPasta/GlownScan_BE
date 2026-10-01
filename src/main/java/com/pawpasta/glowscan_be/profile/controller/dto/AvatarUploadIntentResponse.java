package com.pawpasta.glowscan_be.profile.controller.dto;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

public record AvatarUploadIntentResponse(
        UUID uploadIntentId,
        String uploadUrl,
        String cloudName,
        String apiKey,
        long timestamp,
        String signature,
        String publicId,
        boolean overwrite,
        OffsetDateTime expiresAt,
        AvatarUploadConstraints constraints
) {

    public record AvatarUploadConstraints(
            Set<String> allowedFormats,
            long maxBytes,
            int maxDimension
    ) {
    }
}
