package com.pawpasta.glowscan_be.modal.dto.request;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class VerificationEmailRequest {
    private String recipientEmail;
    private String recipientName;
    private String rawToken;
}
