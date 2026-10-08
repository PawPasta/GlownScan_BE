package com.pawpasta.glowscan_be.skinanalysis.application.dto.response;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

public record AnalysisUploadIntentResponse(
        UUID uploadIntentId,
        String uploadUrl,
        String cloudName,
        String apiKey,
        long timestamp,
        String signature,
        String publicId,
        boolean overwrite,
        OffsetDateTime expiresAt,
        UploadConstraints constraints
) {

    public record UploadConstraints(
            Set<String> allowedFormats,
            long maxBytes,
            int maxDimension
    ) {
    }
}
