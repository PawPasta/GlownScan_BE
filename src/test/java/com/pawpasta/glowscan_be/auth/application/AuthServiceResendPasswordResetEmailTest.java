package com.pawpasta.glowscan_be.auth.application;

import com.pawpasta.glowscan_be.auth.application.dto.request.ResetPasswordRequest;
import com.pawpasta.glowscan_be.auth.domain.ActionToken;
import com.pawpasta.glowscan_be.auth.domain.User;
import com.pawpasta.glowscan_be.auth.domain.enums.ActionTokenPurpose;
import com.pawpasta.glowscan_be.auth.domain.enums.UserStatus;
import com.pawpasta.glowscan_be.auth.infrastructure.repository.ActionTokenRepository;
import com.pawpasta.glowscan_be.auth.infrastructure.repository.RefreshTokenRepository;
import com.pawpasta.glowscan_be.auth.infrastructure.repository.RoleRepository;
import com.pawpasta.glowscan_be.auth.infrastructure.repository.UserDeviceRepository;
import com.pawpasta.glowscan_be.auth.infrastructure.repository.UserRepository;
import com.pawpasta.glowscan_be.auth.infrastructure.repository.UserRoleRepository;
import com.pawpasta.glowscan_be.email.EmailContent;
import com.pawpasta.glowscan_be.shared.security.CurrentUserProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceResendPasswordResetEmailTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private UserRoleRepository userRoleRepository;
    @Mock
    private ActionTokenRepository actionTokenRepository;
    @Mock
    private UserDeviceRepository userDeviceRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private TokenService tokenService;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private ActionEmailService actionEmailService;

    @InjectMocks
    private AuthService authService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "resetPasswordTokenTtl", Duration.ofHours(1));
        ReflectionTestUtils.setField(authService, "resetPasswordResendCooldown", Duration.ofSeconds(30));
    }

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void resendPasswordResetEmailRevokesOldTokenAndSchedulesNewEmail() {
        UUID userId = UUID.randomUUID();
        User user = activeUser(userId);
        ActionToken previousToken = tokenCreatedSecondsAgo(31);

        mockLockedUserLookup(user);
        when(actionTokenRepository.findFirstByUserIdAndPurposeOrderByCreatedAtDesc(
                userId, ActionTokenPurpose.RESET_PASSWORD
        )).thenReturn(Optional.of(previousToken));
        when(tokenService.generateActionToken()).thenReturn("new-reset-token");
        when(tokenService.hashActionToken("new-reset-token")).thenReturn("new-reset-token-hash");

        TransactionSynchronizationManager.initSynchronization();

        String message = authService.resendPasswordResetEmail(
                new ResetPasswordRequest("Member@Example.com")
        );

        assertEquals("Password reset email has been resent.", message);
        InOrder tokenOperations = inOrder(actionTokenRepository);
        tokenOperations.verify(actionTokenRepository).revokePendingByUserIdAndPurpose(
                eq(userId), eq(ActionTokenPurpose.RESET_PASSWORD), any(OffsetDateTime.class)
        );

        ArgumentCaptor<ActionToken> tokenCaptor = ArgumentCaptor.forClass(ActionToken.class);
        tokenOperations.verify(actionTokenRepository).save(tokenCaptor.capture());
        ActionToken newToken = tokenCaptor.getValue();
        assertSame(user, newToken.getUser());
        assertEquals(ActionTokenPurpose.RESET_PASSWORD, newToken.getPurpose());
        assertEquals("new-reset-token-hash", newToken.getTokenHash());
        assertTrue(newToken.getExpiresAt().isAfter(OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(59)));

        verify(actionEmailService, never()).sendActionEmail(any());
        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCommit();
        }

        ArgumentCaptor<EmailContent> emailCaptor = ArgumentCaptor.forClass(EmailContent.class);
        verify(actionEmailService).sendActionEmail(emailCaptor.capture());
        assertEquals("member@example.com", emailCaptor.getValue().getRecipientEmail());
        assertEquals("new-reset-token", emailCaptor.getValue().getRawToken());
        assertEquals(ActionTokenPurpose.RESET_PASSWORD, emailCaptor.getValue().getActionTokenPurpose());
    }

    @Test
    void originalPasswordResetEndpointCannotBypassResendCooldown() {
        UUID userId = UUID.randomUUID();
        User user = activeUser(userId);

        mockLockedUserLookup(user);
        when(actionTokenRepository.findFirstByUserIdAndPurposeOrderByCreatedAtDesc(
                userId, ActionTokenPurpose.RESET_PASSWORD
        )).thenReturn(Optional.of(tokenCreatedSecondsAgo(10)));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> authService.resetPassword(new ResetPasswordRequest("member@example.com"))
        );

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exception.getStatusCode());
        assertTrue(exception.getReason().contains("password reset email"));
        verify(actionTokenRepository, never()).revokePendingByUserIdAndPurpose(any(), any(), any());
        verify(actionTokenRepository, never()).save(any());
        verify(actionEmailService, never()).sendActionEmail(any());
    }

    private void mockLockedUserLookup(User user) {
        when(userRepository.findByEmailAndDeletedAtIsNull("member@example.com")).thenReturn(Optional.of(user));
        when(userRepository.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
    }

    private User activeUser(UUID userId) {
        User user = new User();
        user.setId(userId);
        user.setEmail("member@example.com");
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }

    private ActionToken tokenCreatedSecondsAgo(long seconds) {
        ActionToken token = new ActionToken();
        token.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(seconds));
        return token;
    }
}
