package com.pawpasta.glowscan_be.modal.dto.request;


import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {

    @NotBlank(message = "Email Cannot Be Empty")
    @Email(message = "Email Is Not Verify")
    private String email;
    @NotBlank(message = "Password Cannot Be Empty")
    private String password;
    @NotBlank(message = "Device Cannot Be Empty")
    private String deviceUUid;
    @NotBlank(message = "Device Cannot Be Empty")
    private String deviceName;

}
