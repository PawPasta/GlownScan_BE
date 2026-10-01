package com.pawpasta.glowscan_be.cloudinary.model;

import java.time.OffsetDateTime;

public record SignedUploadRequest(
        String publicId,
        OffsetDateTime expiresAt
) {
}
