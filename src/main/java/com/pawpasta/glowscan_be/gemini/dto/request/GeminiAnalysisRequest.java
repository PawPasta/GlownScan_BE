package com.pawpasta.glowscan_be.gemini.dto.request;

import java.util.Arrays;

public record GeminiAnalysisRequest(
        byte[] imageBytes,
        String mimeType
) {
    public GeminiAnalysisRequest {
        imageBytes = Arrays.copyOf(imageBytes, imageBytes.length);
    }

    @Override
    public byte[] imageBytes() {
        return Arrays.copyOf(imageBytes, imageBytes.length);
    }
}
