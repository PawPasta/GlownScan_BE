package com.pawpasta.glowscan_be.modal.dto.request;


import com.pawpasta.glowscan_be.modal.entity.enums.ActionTokenPurpose;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EmailContentRequest {
    private String recipientEmail;
    private String recipientName;
    private String rawToken;
    private ActionTokenPurpose actionTokenPurpose;
}
