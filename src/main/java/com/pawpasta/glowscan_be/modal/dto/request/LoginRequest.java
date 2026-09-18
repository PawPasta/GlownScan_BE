package com.pawpasta.glowscan_be.modal.dto.request;


import com.fasterxml.jackson.annotation.JsonAlias;
import com.pawpasta.glowscan_be.modal.entity.enums.DevicePlatform;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
    @JsonAlias("deviceUUid")
    private String deviceUuid;
    @NotBlank(message = "Device Cannot Be Empty")
    private String deviceName;
    @NotNull(message = "Device platform is required")
    private DevicePlatform platform;

}
