package com.pawpasta.glowscan_be.auth.controller;

import com.pawpasta.glowscan_be.auth.application.AuthService;
import com.pawpasta.glowscan_be.auth.controller.dto.request.ChangePasswordRequest;
import com.pawpasta.glowscan_be.auth.controller.dto.request.LoginRequest;
import com.pawpasta.glowscan_be.auth.controller.dto.request.RefreshTokenRequest;
import com.pawpasta.glowscan_be.auth.controller.dto.request.RegisterRequest;
import com.pawpasta.glowscan_be.auth.controller.dto.request.ResetPasswordRequest;
import com.pawpasta.glowscan_be.auth.controller.dto.request.VerificationEmailRequest;
import com.pawpasta.glowscan_be.auth.controller.dto.request.VerifyResetPasswordRequest;
import com.pawpasta.glowscan_be.auth.controller.dto.response.LoginResponse;
import com.pawpasta.glowscan_be.shared.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Account registration and authentication endpoints")
public class AuthController {

    private final AuthService authService;

    @PostMapping(value = "/register",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(summary = "Register an account", description = "Creates a pending-verification user account.")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Void> register(@Valid @RequestBody RegisterRequest registerRequest) {
        String message = authService.register(registerRequest);
        return ApiResponse.success(message);
    }

    @PostMapping(value = "/verify-email-token",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(summary = "Verify an email address", description = "Activates a pending account with a valid verification token.")
    public ApiResponse<Void> verifyEmailToken(@Valid @RequestBody VerificationEmailRequest verificationEmailRequest) {
        String message = authService.verifyEmailToken(verificationEmailRequest);
        return ApiResponse.success(message);
    }

    @PostMapping(value = "/login",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(summary = "Log in", description = "Validates credentials, records the device, and returns access and refresh tokens.")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest loginRequest) {
        return ApiResponse.success("Login successful", authService.login(loginRequest));
    }

    @PostMapping(value = "/logout")
    @Operation(summary = "Log out", description = "Revokes the current device session using its access token.")
    public ApiResponse<Void> logout() {
        return ApiResponse.success(authService.logout());
    }

    @PostMapping(value = "/refresh-token",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(summary = "Refresh tokens", description = "Rotates a valid access and refresh token pair.")
    public ApiResponse<LoginResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest refreshTokenRequest) {
        return ApiResponse.success("Tokens refreshed successfully", authService.refreshToken(refreshTokenRequest));
    }

    @PostMapping(value = "/reset-password",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(summary = "Request a password reset", description = "Sends a reset link for an existing active account.")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest resetPasswordRequest) {
        return ApiResponse.success(authService.resetPassword(resetPasswordRequest));
    }

    @PostMapping(value = "/verify-reset-password-token",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(summary = "Reset a password", description = "Consumes a valid reset token and sets the new password.")
    public ApiResponse<Void> resetPassword(
            @Valid @RequestBody VerifyResetPasswordRequest verifyResetPasswordRequest
    ) {
        return ApiResponse.success(authService.resetPassword(verifyResetPasswordRequest));
    }

    @PostMapping(value = "/change-password",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(summary = "Change the current password", description = "Requires an authenticated user and the current password.")
    public ApiResponse<Void> changePassword(
            @Valid @RequestBody ChangePasswordRequest changePasswordRequest
    ) {
        return ApiResponse.success(authService.changePassword(changePasswordRequest));
    }

}
