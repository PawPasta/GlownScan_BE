package com.pawpasta.glowscan_be.service;

import com.pawpasta.glowscan_be.modal.entity.User;

public interface JwtService {
    String generateToken(User user);

}
