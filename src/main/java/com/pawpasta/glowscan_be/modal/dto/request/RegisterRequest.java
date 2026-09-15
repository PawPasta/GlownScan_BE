package com.pawpasta.glowscan_be.modal.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {
    @NotBlank(message = "Email cannot be empty")
    @Email(message = "Email Format Incorrect ")
    private String email;

    @NotBlank(message = "Password cannot be empty")
    private String password;

    @NotBlank(message = "full name cannot be empty")
    @Size(max = 150, message = "Full name (maximum 150 characters)")
    private String fullName;

    @NotBlank(message = "Password cannot be empty")
    private String confirmPassword;
}
