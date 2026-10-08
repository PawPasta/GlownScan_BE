package com.pawpasta.glowscan_be.gemini.dto.response;

import com.pawpasta.glowscan_be.skinanalysis.domain.enums.ObservationType;
import com.pawpasta.glowscan_be.skinanalysis.domain.enums.ObservationVisibility;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record GeminiAnalysisResponse(
        boolean qualityAccepted,
        List<String> qualityIssues,
        List<Observation> observations,
        List<String> limitations,
        Map<String, Object> providerPayload,
        String modelVersion
) {

    public record Observation(
            ObservationType type,
            short severity,
            BigDecimal confidence,
            ObservationVisibility visibility,
            String note,
            List<Integer> box2d
    ) {
    }
}
