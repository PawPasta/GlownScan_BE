package com.pawpasta.glowscan_be.skinanalysis.application.dto.response;

import com.pawpasta.glowscan_be.skinanalysis.domain.enums.AnalysisStatus;

import java.util.UUID;

public record SkinAnalysisSubmitResponse(
        UUID analysisId,
        AnalysisStatus status
) {
}
