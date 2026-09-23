package com.pawpasta.glowscan_be.auth.controller.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VerifyResetPasswordRequest {

    @NotBlank(message = "Email cannot be empty")
    @Email(message = "Email format is invalid")
    private String email;

    @NotBlank(message = "Reset password token cannot be empty")
    @JsonAlias("rawToken")
    private String resetPasswordToken;

    @NotBlank(message = "New password cannot be empty")
    private String newPassword;

    @NotBlank(message = "Password confirmation cannot be empty")
    private String confirmPassword;
}
