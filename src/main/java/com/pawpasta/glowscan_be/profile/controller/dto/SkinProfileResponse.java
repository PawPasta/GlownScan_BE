package com.pawpasta.glowscan_be.profile.controller.dto;

import com.pawpasta.glowscan_be.profile.domain.enums.SensitivityLevel;
import com.pawpasta.glowscan_be.profile.domain.enums.SkinType;

import java.time.OffsetDateTime;
import java.util.UUID;

public record SkinProfileResponse(
        UUID id,
        SkinType skinType,
        SensitivityLevel sensitivityLevel,
        String notes,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
