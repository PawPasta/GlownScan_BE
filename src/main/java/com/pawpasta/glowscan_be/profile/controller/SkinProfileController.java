package com.pawpasta.glowscan_be.profile.controller;

import com.pawpasta.glowscan_be.profile.application.ProfileService;
import com.pawpasta.glowscan_be.profile.controller.dto.SkinProfileResponse;
import com.pawpasta.glowscan_be.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/profile/skin")
@RequiredArgsConstructor
@Tag(name = "Profile", description = "Authenticated profile endpoints")
public class SkinProfileController {

    private final ProfileService profileService;

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get the current skin profile", description = "Returns the current derived skin profile for the authenticated user.")
    public ApiResponse<SkinProfileResponse> getSkinProfile() {
        return ApiResponse.success(
                "Skin profile retrieved successfully",
                profileService.getCurrentSkinProfile()
        );
    }
}
