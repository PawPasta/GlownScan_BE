package com.pawpasta.glowscan_be.shared.security;

import com.pawpasta.glowscan_be.auth.infrastructure.handler.AuthExceptionHandler;
import com.pawpasta.glowscan_be.auth.domain.User;
import com.pawpasta.glowscan_be.auth.domain.enums.UserStatus;
import com.pawpasta.glowscan_be.auth.infrastructure.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.UUID;

import static com.pawpasta.glowscan_be.auth.infrastructure.handler.AuthExceptionHandler.accessTokenInvalidOrExpired;

@Component
@RequiredArgsConstructor
public class CurrentUserProvider {

    private final UserRepository userRepository;

    public String getAccessToken() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
            throw accessTokenInvalidOrExpired();
        }

        return jwtAuthentication.getToken().getTokenValue();
    }
    public User getUserContext() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
            throw accessTokenInvalidOrExpired();
        }

        final UUID userId;
        try {
            userId = UUID.fromString(Objects.requireNonNull(jwtAuthentication.getToken().getClaimAsString("uid")));
        } catch (IllegalArgumentException | ClassCastException | NullPointerException exception) {
            throw accessTokenInvalidOrExpired();
        }

        return userRepository.findById(userId)
                .filter(user -> user.getDeletedAt() == null)
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(AuthExceptionHandler::userNotAuthorized);
    }

}
