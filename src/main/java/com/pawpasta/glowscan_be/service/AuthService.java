package com.pawpasta.glowscan_be.service;

import com.pawpasta.glowscan_be.modal.dto.request.*;
import com.pawpasta.glowscan_be.modal.dto.response.LoginResponse;

public interface AuthService {

     String register(RegisterRequest registerRequest);
     String verifyEmailToken(VerificationEmailRequest verificationEmailRequest);
     LoginResponse login(LoginRequest loginRequest);
     String logout(LogoutRequest logoutRequest);
     String refreshToken(RefreshTokenRequest refreshTokenRequest);
}