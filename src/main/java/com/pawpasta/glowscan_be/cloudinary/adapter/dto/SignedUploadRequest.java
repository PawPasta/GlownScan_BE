package com.pawpasta.glowscan_be.cloudinary.adapter.dto;

import java.time.OffsetDateTime;

public record SignedUploadRequest(
        String publicId,
        OffsetDateTime expiresAt
) {
}
