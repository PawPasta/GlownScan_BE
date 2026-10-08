package com.pawpasta.glowscan_be.skinanalysis.application.dto.request;

import com.pawpasta.glowscan_be.skinanalysis.domain.enums.AnalysisKind;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record SubmitSkinAnalysisRequest(
        @NotNull(message = "Upload intent id is required") UUID uploadIntentId,
        @NotNull(message = "Analysis kind is required") AnalysisKind analysisKind,
        @NotBlank(message = "Consent version is required") String consentVersion,
        @NotNull(message = "Assessment is required") @Valid AssessmentRequest assessment
) {
}
