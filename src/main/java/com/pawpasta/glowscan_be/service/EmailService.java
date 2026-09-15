package com.pawpasta.glowscan_be.service;

import com.pawpasta.glowscan_be.modal.dto.request.EmailContentRequest;


public interface EmailService {

    void sendActionEmail(EmailContentRequest emailRequest);
}
