package com.pawpasta.glowscan_be.profile.controller;

import com.pawpasta.glowscan_be.profile.application.ProfileService;
import com.pawpasta.glowscan_be.profile.controller.dto.AvatarUploadIntentResponse;
import com.pawpasta.glowscan_be.profile.controller.dto.UpdateAvatarRequest;
import com.pawpasta.glowscan_be.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/profile/avatar")
@RequiredArgsConstructor
@Tag(name = "Profile", description = "Authenticated profile endpoints")
public class ProfileController {

    private final ProfileService profileService;

    @PostMapping(value = "/upload-intent", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create an avatar upload intent", description = "Returns short-lived signed Cloudinary upload parameters for the current user.")
    public ApiResponse<AvatarUploadIntentResponse> createAvatarUploadIntent() {
        return ApiResponse.success(
                "Avatar upload intent created successfully",
                profileService.createAvatarUploadIntent()
        );
    }

    @PatchMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Confirm an avatar upload", description = "Stores the current user's avatar after a successful direct upload to Cloudinary.")
    public ApiResponse<Void> confirmAvatar(@Valid @RequestBody UpdateAvatarRequest request) {
        return ApiResponse.success(profileService.confirmAvatar(request));
    }
}
