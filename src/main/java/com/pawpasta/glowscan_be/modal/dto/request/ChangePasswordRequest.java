package com.pawpasta.glowscan_be.modal.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChangePasswordRequest {

    @NotBlank(message = "Current password cannot be empty")
    private String oldPassword;

    @NotBlank(message = "New password cannot be empty")
    private String newPassword;

    @NotBlank(message = "Password confirmation cannot be empty")
    private String confirmPassword;
}
