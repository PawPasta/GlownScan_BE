package com.pawpasta.glowscan_be.skinanalysis.controller;

import com.pawpasta.glowscan_be.shared.dto.ApiResponse;
import com.pawpasta.glowscan_be.skinanalysis.application.SkinAnalysisService;
import com.pawpasta.glowscan_be.skinanalysis.application.dto.request.SubmitSkinAnalysisRequest;
import com.pawpasta.glowscan_be.skinanalysis.application.dto.response.AnalysisUploadIntentResponse;
import com.pawpasta.glowscan_be.skinanalysis.application.dto.response.SkinAnalysisResponse;
import com.pawpasta.glowscan_be.skinanalysis.application.dto.response.SkinAnalysisSubmitResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/skin-analyses")
@RequiredArgsConstructor
@Tag(name = "Skin analysis", description = "Authenticated asynchronous skin analysis endpoints")
public class SkinAnalysisController {

    private final SkinAnalysisService skinAnalysisService;

    @PostMapping(value = "/upload-intent", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create a skin-analysis upload intent")
    public ApiResponse<AnalysisUploadIntentResponse> createUploadIntent() {
        return ApiResponse.success(
                "Skin analysis upload intent created successfully",
                skinAnalysisService.createUploadIntent()
        );
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Queue a skin analysis")
    public ResponseEntity<ApiResponse<SkinAnalysisSubmitResponse>> submit(
            @Valid @RequestBody SubmitSkinAnalysisRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        return ResponseEntity.accepted().body(ApiResponse.success(
                "Skin analysis queued",
                skinAnalysisService.submit(request, idempotencyKey)
        ));
    }

    @GetMapping(value = "/{analysisId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get a skin analysis status and result")
    public ApiResponse<SkinAnalysisResponse> get(@PathVariable UUID analysisId) {
        return ApiResponse.success(
                "Skin analysis retrieved successfully",
                skinAnalysisService.get(analysisId)
        );
    }
}
