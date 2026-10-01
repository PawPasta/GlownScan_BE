package com.pawpasta.glowscan_be.email;


import com.pawpasta.glowscan_be.auth.domain.enums.ActionTokenPurpose;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EmailContent {
    private String recipientEmail;
    private String recipientName;
    private String rawToken;
    private ActionTokenPurpose actionTokenPurpose;
}
