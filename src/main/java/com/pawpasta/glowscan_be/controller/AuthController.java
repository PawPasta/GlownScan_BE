package com.pawpasta.glowscan_be.controller;

import com.pawpasta.glowscan_be.modal.dto.request.LoginRequest;
import com.pawpasta.glowscan_be.modal.dto.request.RegisterRequest;
import com.pawpasta.glowscan_be.modal.dto.request.VerificationEmailRequest;
import com.pawpasta.glowscan_be.modal.dto.response.LoginResponse;
import com.pawpasta.glowscan_be.service.AuthService;
import com.pawpasta.glowscan_be.util.ResponseUtil;
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
    public ResponseUtil<Void> register(@Valid @RequestBody RegisterRequest registerRequest) {
        String message = authService.register(registerRequest);
        return ResponseUtil.success(message);
    }

    @PostMapping(value = "/verify-email-token",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(summary = "Verify an email address", description = "Activates a pending account with a valid verification token.")
    public ResponseUtil<Void> verifyEmailToken(@Valid @RequestBody VerificationEmailRequest verificationEmailRequest) {
        String message = authService.verifyEmailToken(verificationEmailRequest);
        return ResponseUtil.success(message);
    }

    @PostMapping(value = "/login",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(summary = "Log in", description = "Validates credentials, records the device, and returns access and refresh tokens.")
    public ResponseUtil<LoginResponse> login(@Valid @RequestBody LoginRequest loginRequest) {
        return ResponseUtil.success("Login successful", authService.login(loginRequest));
    }

}
