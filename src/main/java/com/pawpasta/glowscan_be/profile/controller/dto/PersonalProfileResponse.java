package com.pawpasta.glowscan_be.profile.controller.dto;

import com.pawpasta.glowscan_be.profile.domain.enums.Gender;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PersonalProfileResponse(
        UUID id,
        String fullName,
        String avatarUrl,
        LocalDate dateOfBirth,
        Gender gender,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
