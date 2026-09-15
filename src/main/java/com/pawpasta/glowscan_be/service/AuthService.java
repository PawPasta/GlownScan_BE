package com.pawpasta.glowscan_be.service;

import com.pawpasta.glowscan_be.modal.dto.request.LoginRequest;
import com.pawpasta.glowscan_be.modal.dto.request.LogoutRequest;
import com.pawpasta.glowscan_be.modal.dto.request.RefreshTokenRequest;
import com.pawpasta.glowscan_be.modal.dto.request.RegisterRequest;
import com.pawpasta.glowscan_be.modal.dto.response.LoginResponse;

public interface AuthService {

     String register(RegisterRequest registerRequest);
     LoginResponse login(LoginRequest loginRequest);
     String logout(LogoutRequest logoutRequest);
     String refreshToken(RefreshTokenRequest refreshTokenRequest);
}