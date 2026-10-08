package com.pawpasta.glowscan_be.skinanalysis.application.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.Map;

public record AssessmentRequest(
        @NotBlank(message = "Questionnaire version cannot be empty") String questionnaireVersion,
        @NotEmpty(message = "Assessment answers cannot be empty") Map<String, Object> answers
) {
}
