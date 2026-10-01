package com.pawpasta.glowscan_be.cloudinary.model;

import java.time.OffsetDateTime;
import java.util.Set;

public record SignedUploadIntent(
        String uploadUrl,
        String cloudName,
        String apiKey,
        long timestamp,
        String signature,
        String publicId,
        boolean overwrite,
        Set<String> allowedFormats,
        OffsetDateTime expiresAt
) {
}
