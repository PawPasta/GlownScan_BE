package com.pawpasta.glowscan_be.util;

import com.pawpasta.glowscan_be.handler.AuthExceptionHandler;
import com.pawpasta.glowscan_be.modal.entity.User;
import com.pawpasta.glowscan_be.modal.entity.enums.UserStatus;
import com.pawpasta.glowscan_be.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.UUID;

import static com.pawpasta.glowscan_be.handler.AuthExceptionHandler.accessTokenInvalidOrExpired;

@Component
@RequiredArgsConstructor
public class SecurityUtil {

    private final UserRepository userRepository;
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
