package com.pawpasta.glowscan_be.skinanalysis.application.dto.response;

import com.pawpasta.glowscan_be.skinanalysis.domain.enums.AnalysisStatus;
import com.pawpasta.glowscan_be.skinanalysis.domain.enums.ObservationType;
import com.pawpasta.glowscan_be.skinanalysis.domain.enums.ObservationVisibility;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record SkinAnalysisResponse(
        UUID analysisId,
        AnalysisStatus status,
        OffsetDateTime createdAt,
        OffsetDateTime completedAt,
        String imageUrl,
        Quality quality,
        List<Observation> observations,
        List<String> limitations
) {

    public record Quality(boolean accepted, List<String> issues) {
    }

    public record Observation(
            UUID id,
            ObservationType type,
            Short severity,
            BigDecimal confidence,
            ObservationVisibility visibility,
            String note,
            List<Integer> box2d
    ) {
    }
}
