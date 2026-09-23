package com.pawpasta.glowscan_be.auth.controller.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@AllArgsConstructor
@NoArgsConstructor
public class VerificationEmailRequest {
    @NotBlank(message = "Email cannot be empty")
    @Email(message = "Email Format Incorrect")
    private String email;

    @NotBlank(message = "Token cannot be empty")
    private String rawToken;
}
