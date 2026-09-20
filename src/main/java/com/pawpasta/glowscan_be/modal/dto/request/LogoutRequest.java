package com.pawpasta.glowscan_be.modal.dto.request;


import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LogoutRequest {

    @NotBlank(message = "Access token is required")
    private String accessToken;
}
