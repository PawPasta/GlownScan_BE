package com.pawpasta.glowscan_be.profile.controller;

import com.pawpasta.glowscan_be.profile.application.ProfileService;
import com.pawpasta.glowscan_be.profile.controller.dto.PersonalProfileResponse;
import com.pawpasta.glowscan_be.profile.controller.dto.UpdatePersonalProfileRequest;
import com.pawpasta.glowscan_be.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
@Tag(name = "Profile", description = "Authenticated profile endpoints")
public class PersonalProfileController {

    private final ProfileService profileService;

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get the current personal profile", description = "Returns the personal profile for the authenticated user.")
    public ApiResponse<PersonalProfileResponse> getPersonalProfile() {
        return ApiResponse.success(
                "Personal profile retrieved successfully",
                profileService.getCurrentPersonalProfile()
        );
    }

    @PatchMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Update the current personal profile", description = "Partially updates non-avatar profile fields for the authenticated user.")
    public ApiResponse<PersonalProfileResponse> updatePersonalProfile(
            @Valid @RequestBody UpdatePersonalProfileRequest request
    ) {
        return ApiResponse.success(
                "Personal profile updated successfully",
                profileService.updateCurrentPersonalProfile(request)
        );
    }
}
