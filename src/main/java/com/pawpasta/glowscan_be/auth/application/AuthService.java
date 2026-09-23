package com.pawpasta.glowscan_be.auth.application;

import com.pawpasta.glowscan_be.auth.infrastructure.handler.AuthExceptionHandler;
import com.pawpasta.glowscan_be.auth.controller.dto.request.ChangePasswordRequest;
import com.pawpasta.glowscan_be.email.EmailContent;
import com.pawpasta.glowscan_be.auth.controller.dto.request.LoginRequest;
import com.pawpasta.glowscan_be.auth.controller.dto.request.RefreshTokenRequest;
import com.pawpasta.glowscan_be.auth.controller.dto.request.RegisterRequest;
import com.pawpasta.glowscan_be.auth.controller.dto.request.ResetPasswordRequest;
import com.pawpasta.glowscan_be.auth.controller.dto.request.VerificationEmailRequest;
import com.pawpasta.glowscan_be.auth.controller.dto.request.VerifyResetPasswordRequest;
import com.pawpasta.glowscan_be.auth.controller.dto.response.LoginResponse;
import com.pawpasta.glowscan_be.auth.domain.ActionToken;
import com.pawpasta.glowscan_be.auth.domain.RefreshToken;
import com.pawpasta.glowscan_be.auth.domain.Role;
import com.pawpasta.glowscan_be.auth.domain.User;
import com.pawpasta.glowscan_be.auth.domain.UserDevice;
import com.pawpasta.glowscan_be.auth.domain.UserRole;
import com.pawpasta.glowscan_be.auth.domain.enums.ActionTokenPurpose;
import com.pawpasta.glowscan_be.auth.domain.enums.UserStatus;
import com.pawpasta.glowscan_be.auth.infrastructure.repository.ActionTokenRepository;
import com.pawpasta.glowscan_be.auth.infrastructure.repository.RefreshTokenRepository;
import com.pawpasta.glowscan_be.auth.infrastructure.repository.RoleRepository;
import com.pawpasta.glowscan_be.auth.infrastructure.repository.UserDeviceRepository;
import com.pawpasta.glowscan_be.auth.infrastructure.repository.UserRepository;
import com.pawpasta.glowscan_be.auth.infrastructure.repository.UserRoleRepository;
import com.pawpasta.glowscan_be.shared.security.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Supplier;

import static com.pawpasta.glowscan_be.auth.infrastructure.handler.AuthExceptionHandler.*;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String DEFAULT_ROLE_CODE = "USER";
    private static final String PASSWORD_REGEX = "^(?=.*[A-Z])(?=.*[\\p{P}\\p{S}]).{6,12}$";
    private static final String REGISTRATION_SUCCESS_MESSAGE = "Registration successful";
    private static final String EMAIL_VERIFIED_SUCCESS_MESSAGE = "Email verified successfully";
    private static final String LOGOUT_SUCCESS_MESSAGE = "Logged out successfully";
    private static final String PASSWORD_RESET_LINK_SENT_MESSAGE = "Password reset link has been sent.";
    private static final String PASSWORD_RESET_SUCCESS_MESSAGE = "Password reset successfully";
    private static final String PASSWORD_CHANGED_SUCCESS_MESSAGE = "Password changed successfully";
    private static final String PASSWORD_RESET_REVOKE_REASON = "PASSWORD_RESET";
    private static final String PASSWORD_CHANGED_REVOKE_REASON = "PASSWORD_CHANGED";

    @Value("${app.max.failed.login}")
    private int maxFailedLoginAttempts;

    @Value("${app.login.lock}")
    private Duration loginLockDuration;

    @Value("${app.tokens.verify-email-ttl}")
    private Duration verifyEmailTokenTtl;

    @Value("${app.tokens.reset-password-ttl}")
    private Duration resetPasswordTokenTtl;

    @Value("${app.tokens.refresh-token-ttl}")
    private Duration refreshTokenTtl;

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final ActionTokenRepository actionTokenRepository;
    private final UserDeviceRepository userDeviceRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final TokenService tokenService;
    private final CurrentUserProvider currentUserProvider;
    private final ActionEmailService actionEmailService;

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw emailCannotBeEmpty();
        }

        return email.strip().toLowerCase(Locale.ROOT);
    }

    private void validateNewPassword(String password, String confirmPassword) {
        if (password == null || !password.matches(PASSWORD_REGEX)) {
            throw passwordPolicyInvalid();
        }

        if (!password.equals(confirmPassword)) {
            throw passwordConfirmationDoesNotMatch();
        }
    }

    private boolean passwordMatches(String rawPassword, String passwordHash) {
        try {
            return rawPassword != null && passwordHash != null && BCrypt.checkpw(rawPassword, passwordHash);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private boolean isActiveAccount(User user) {
        return user != null
                && user.getDeletedAt() == null
                && user.getStatus() == UserStatus.ACTIVE;
    }

    private void requireActiveAccount(User user, Supplier<ResponseStatusException> exceptionSupplier) {
        if (!isActiveAccount(user)) {
            throw exceptionSupplier.get();
        }
    }

    private void incrementTokenVersion(User user) {
        int currentVersion = user.getTokenVersion() == null ? 1 : user.getTokenVersion();
        user.setTokenVersion(Math.max(1, currentVersion) + 1);
    }

    private void resetLoginFailureState(User user) {
        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
    }

    private void verifyAccountCanLogin(User user, OffsetDateTime now) {
        if (user == null || user.getDeletedAt() != null) {
            throw accountCannotLogIn();
        }

        if (user.getStatus() == UserStatus.LOCKED) {
            if (user.getLockedUntil() != null && !user.getLockedUntil().isAfter(now)) {
                user.setStatus(UserStatus.ACTIVE);
                resetLoginFailureState(user);
            } else {
                throw accountTemporarilyLocked();
            }
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw accountCannotLogIn();
        }
    }

    private void recordFailedLogin(User user, OffsetDateTime now) {
        int failedAttempts = (user.getFailedLoginCount() == null ? 0 : user.getFailedLoginCount()) + 1;
        user.setFailedLoginCount(failedAttempts);

        if (failedAttempts >= maxFailedLoginAttempts) {
            user.setStatus(UserStatus.LOCKED);
            user.setLockedUntil(now.plus(loginLockDuration));
        }
    }

    private UserDevice saveUserDevice(User user, LoginRequest loginRequest, OffsetDateTime now) {
        if (loginRequest.getDeviceUuid() == null || loginRequest.getDeviceUuid().isBlank()
                || loginRequest.getDeviceName() == null || loginRequest.getDeviceName().isBlank()) {
            throw deviceInformationRequired();
        }

        if (loginRequest.getPlatform() == null) {
            throw devicePlatformRequired();
        }

        UserDevice device = userDeviceRepository.findByUserAndDeviceUuid(user, loginRequest.getDeviceUuid())
                .orElseGet(UserDevice::new);
        device.setUser(user);
        device.setDeviceUuid(loginRequest.getDeviceUuid());
        device.setDeviceName(loginRequest.getDeviceName());
        device.setPlatform(loginRequest.getPlatform());
        device.setLastActiveAt(now);

        return userDeviceRepository.save(device);
    }

    private void createAndScheduleActionToken(
            User user,
            ActionTokenPurpose purpose,
            Duration tokenTtl,
            OffsetDateTime now
    ) {
        String rawToken = tokenService.generateActionToken();
        ActionToken actionToken = new ActionToken();
        actionToken.setUser(user);
        actionToken.setPurpose(purpose);
        actionToken.setTokenHash(tokenService.hashActionToken(rawToken));
        actionToken.setExpiresAt(now.plus(tokenTtl));
        actionTokenRepository.save(actionToken);

        scheduleEmailAfterCommit(new EmailContent(
                user.getEmail(), null, rawToken, purpose
        ));
    }

    private ActionToken findValidActionToken(
            String email,
            String rawToken,
            ActionTokenPurpose purpose,
            OffsetDateTime now,
            Supplier<ResponseStatusException> invalidTokenException
    ) {
        if (rawToken == null || rawToken.isBlank()) {
            throw invalidTokenException.get();
        }

        ActionToken actionToken = actionTokenRepository
                .findPendingByUserEmailAndTokenHashAndPurpose(
                        email,
                        tokenService.hashActionToken(rawToken.strip()),
                        purpose
                )
                .orElseThrow(invalidTokenException);

        if (actionToken.getExpiresAt() == null || !actionToken.getExpiresAt().isAfter(now)) {
            throw invalidTokenException.get();
        }

        return actionToken;
    }

    private User findLockedUserByEmail(String email) {
        User user = userRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(AuthExceptionHandler::emailNotFound);

        if (user.getId() == null) {
            throw emailNotFound();
        }

        return userRepository.findByIdForUpdate(user.getId())
                .orElseThrow(AuthExceptionHandler::emailNotFound);
    }

    private User findLockedActionTokenUser(ActionToken actionToken) {
        if (actionToken.getUser() == null || actionToken.getUser().getId() == null) {
            throw passwordResetLinkInvalidOrExpired();
        }

        return userRepository.findByIdForUpdate(actionToken.getUser().getId())
                .orElseThrow(AuthExceptionHandler::passwordResetLinkInvalidOrExpired);
    }

    private void updatePasswordAndRevokeSessions(
            User user,
            String rawNewPassword,
            OffsetDateTime now,
            String revokeReason
    ) {
        user.setPasswordHash(BCrypt.hashpw(rawNewPassword, BCrypt.gensalt()));
        incrementTokenVersion(user);
        userRepository.save(user);
        refreshTokenRepository.revokeActiveTokensByUserId(user.getId(), now, revokeReason);
    }

    private void scheduleEmailAfterCommit(EmailContent emailRequest) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                actionEmailService.sendActionEmail(emailRequest);
            }
        });
    }

    @Transactional
    public String register(RegisterRequest registerRequest) {
        if (registerRequest == null) {
            throw registerRequestRequired();
        }

        validateNewPassword(registerRequest.getPassword(), registerRequest.getConfirmPassword());

        String email = normalizeEmail(registerRequest.getEmail());
        if (userRepository.existsByEmailAndDeletedAtIsNull(email)) {
            throw emailAlreadyExists();
        }

        Role defaultRole = roleRepository.findByCode(DEFAULT_ROLE_CODE)
                .orElseThrow(AuthExceptionHandler::defaultUserRoleNotConfigured);

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(BCrypt.hashpw(registerRequest.getPassword(), BCrypt.gensalt()));
        user.setStatus(UserStatus.PENDING_VERIFICATION);
        user.setTokenVersion(1);
        user.setFailedLoginCount(0);
        userRepository.save(user);

        UserRole userRole = new UserRole();
        userRole.setUser(user);
        userRole.setRole(defaultRole);
        userRoleRepository.save(userRole);

        createAndScheduleActionToken(
                user,
                ActionTokenPurpose.VERIFY_EMAIL,
                verifyEmailTokenTtl,
                OffsetDateTime.now(ZoneOffset.UTC)
        );

        return REGISTRATION_SUCCESS_MESSAGE;
    }

    @Transactional
    public String verifyEmailToken(VerificationEmailRequest verificationEmailRequest) {
        if (verificationEmailRequest == null) {
            throw verificationEmailRequestRequired();
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        ActionToken verificationToken = findValidActionToken(
                normalizeEmail(verificationEmailRequest.getEmail()),
                verificationEmailRequest.getRawToken(),
                ActionTokenPurpose.VERIFY_EMAIL,
                now,
                AuthExceptionHandler::verificationLinkInvalidOrExpired
        );

        User user = verificationToken.getUser();
        if (user == null || user.getStatus() != UserStatus.PENDING_VERIFICATION) {
            throw userNotPendingVerification();
        }

        user.setStatus(UserStatus.ACTIVE);
        user.setEmailVerifiedAt(now);
        verificationToken.setConsumedAt(now);
        userRepository.save(user);
        actionTokenRepository.save(verificationToken);

        return EMAIL_VERIFIED_SUCCESS_MESSAGE;
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public LoginResponse login(LoginRequest loginRequest) {
        if (loginRequest == null || loginRequest.getEmail() == null || loginRequest.getEmail().isBlank()
                || loginRequest.getPassword() == null || loginRequest.getPassword().isBlank()) {
            throw emailAndPasswordRequired();
        }

        User user = userRepository.findByEmailAndDeletedAtIsNull(normalizeEmail(loginRequest.getEmail()))
                .orElseThrow(AuthExceptionHandler::invalidCredentials);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        verifyAccountCanLogin(user, now);

        if (!passwordMatches(loginRequest.getPassword(), user.getPasswordHash())) {
            recordFailedLogin(user, now);
            userRepository.save(user);
            throw invalidCredentials();
        }

        resetLoginFailureState(user);
        user.setLastLoginAt(now);
        userRepository.save(user);

        UserDevice device = saveUserDevice(user, loginRequest, now);
        String rawRefreshToken = tokenService.generateActionToken();

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setDevice(device);
        refreshToken.setTokenHash(tokenService.hashActionToken(rawRefreshToken));
        refreshToken.setTokenFamilyId(UUID.randomUUID());
        refreshToken.setExpiresAt(now.plus(refreshTokenTtl));
        refreshTokenRepository.save(refreshToken);

        return new LoginResponse(tokenService.generateJWTToken(user), rawRefreshToken);
    }

    @Transactional
    public String logout() {

        User contextUser = currentUserProvider.getUserContext();
        if (contextUser == null || contextUser.getId() == null) {
            throw userNotAuthorized();
        }

        try {
            Jwt jwt = tokenService.decodeJWTToken(currentUserProvider.getAccessToken().strip());
            String userIdClaim = jwt.getClaimAsString("uid");
            if (userIdClaim == null || userIdClaim.isBlank()) {
                throw accessTokenInvalidOrExpired();
            }

            refreshTokenRepository.revokeActiveTokensByUserId(
                    UUID.fromString(userIdClaim),
                    OffsetDateTime.now(ZoneOffset.UTC),
                    "LOGOUT"
            );
        } catch (JwtException | IllegalArgumentException exception) {
            throw accessTokenInvalidOrExpired();
        }

        return LOGOUT_SUCCESS_MESSAGE;
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public LoginResponse refreshToken(RefreshTokenRequest refreshTokenRequest) {
        if (refreshTokenRequest == null || refreshTokenRequest.getAccessToken() == null
                || refreshTokenRequest.getAccessToken().isBlank()
                || refreshTokenRequest.getRefreshToken() == null
                || refreshTokenRequest.getRefreshToken().isBlank()) {
            throw accessAndRefreshTokenRequired();
        }

        try {
            Jwt jwt = tokenService.decodeJWTTokenForRefresh(refreshTokenRequest.getAccessToken().strip());
            String userIdClaim = jwt.getClaimAsString("uid");
            if (userIdClaim == null || userIdClaim.isBlank()) {
                throw accessTokenInvalidOrExpired();
            }
        } catch (JwtException | IllegalArgumentException exception) {
            throw accessTokenInvalidOrExpired();
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        RefreshToken currentRefreshToken = refreshTokenRepository
                .findByTokenHash(tokenService.hashActionToken(refreshTokenRequest.getRefreshToken().strip()))
                .orElseThrow(AuthExceptionHandler::refreshTokenInvalidOrExpired);

        if (currentRefreshToken.getUser() == null || currentRefreshToken.getUser().getId() == null
                || currentRefreshToken.getTokenFamilyId() == null
                || currentRefreshToken.getExpiresAt() == null
                || !currentRefreshToken.getExpiresAt().isAfter(now)) {
            throw refreshTokenInvalidOrExpired();
        }

        if (currentRefreshToken.getRevokedAt() != null) {
            refreshTokenRepository.revokeActiveTokensByFamilyId(
                    currentRefreshToken.getTokenFamilyId(),
                    now,
                    "REFRESH_TOKEN_REUSE"
            );
            throw refreshTokenInvalidOrExpired();
        }

        verifyAccountCanLogin(currentRefreshToken.getUser(), now);

        currentRefreshToken.setRevokedAt(now);
        currentRefreshToken.setRevokeReason("ROTATED");
        currentRefreshToken = refreshTokenRepository.saveAndFlush(currentRefreshToken);

        String rawRefreshToken = tokenService.generateActionToken();
        RefreshToken replacementRefreshToken = new RefreshToken();
        replacementRefreshToken.setUser(currentRefreshToken.getUser());
        replacementRefreshToken.setDevice(currentRefreshToken.getDevice());
        replacementRefreshToken.setTokenHash(tokenService.hashActionToken(rawRefreshToken));
        replacementRefreshToken.setTokenFamilyId(currentRefreshToken.getTokenFamilyId());
        replacementRefreshToken.setExpiresAt(now.plus(refreshTokenTtl));
        replacementRefreshToken = refreshTokenRepository.save(replacementRefreshToken);

        currentRefreshToken.setReplacedByToken(replacementRefreshToken);
        refreshTokenRepository.save(currentRefreshToken);

        return new LoginResponse(
                tokenService.generateJWTToken(currentRefreshToken.getUser()),
                rawRefreshToken
        );
    }

    @Transactional
    public String resetPassword(ResetPasswordRequest resetPasswordRequest) {
        if (resetPasswordRequest == null) {
            throw resetPasswordRequestRequired();
        }

        User user = findLockedUserByEmail(normalizeEmail(resetPasswordRequest.getEmail()));
        requireActiveAccount(user, AuthExceptionHandler::accountCannotResetPassword);

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        actionTokenRepository.revokePendingByUserIdAndPurpose(
                user.getId(),
                ActionTokenPurpose.RESET_PASSWORD,
                now
        );
        createAndScheduleActionToken(user, ActionTokenPurpose.RESET_PASSWORD, resetPasswordTokenTtl, now);

        return PASSWORD_RESET_LINK_SENT_MESSAGE;
    }

    @Transactional
    public String resetPassword(VerifyResetPasswordRequest verifyResetPasswordRequest) {
        if (verifyResetPasswordRequest == null) {
            throw resetPasswordRequestRequired();
        }

        validateNewPassword(
                verifyResetPasswordRequest.getNewPassword(),
                verifyResetPasswordRequest.getConfirmPassword()
        );

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        ActionToken resetPasswordToken = findValidActionToken(
                normalizeEmail(verifyResetPasswordRequest.getEmail()),
                verifyResetPasswordRequest.getResetPasswordToken(),
                ActionTokenPurpose.RESET_PASSWORD,
                now,
                AuthExceptionHandler::passwordResetLinkInvalidOrExpired
        );

        User user = findLockedActionTokenUser(resetPasswordToken);
        requireActiveAccount(user, AuthExceptionHandler::accountCannotResetPassword);

        resetLoginFailureState(user);
        updatePasswordAndRevokeSessions(user, verifyResetPasswordRequest.getNewPassword(), now, PASSWORD_RESET_REVOKE_REASON);
        resetPasswordToken.setConsumedAt(now);
        actionTokenRepository.save(resetPasswordToken);

        return PASSWORD_RESET_SUCCESS_MESSAGE;
    }

    @Transactional
    public String changePassword(ChangePasswordRequest changePasswordRequest) {
        if (changePasswordRequest == null) {
            throw changePasswordRequestRequired();
        }

        validateNewPassword(changePasswordRequest.getNewPassword(), changePasswordRequest.getConfirmPassword());

        User contextUser = currentUserProvider.getUserContext();
        if (contextUser == null || contextUser.getId() == null) {
            throw userNotAuthorized();
        }

        User user = userRepository.findByIdForUpdate(contextUser.getId())
                .orElseThrow(AuthExceptionHandler::userNotAuthorized);
        requireActiveAccount(user, AuthExceptionHandler::accountCannotChangePassword);

        if (!passwordMatches(changePasswordRequest.getOldPassword(), user.getPasswordHash())) {
            throw currentPasswordIncorrect();
        }

        if (passwordMatches(changePasswordRequest.getNewPassword(), user.getPasswordHash())) {
            throw newPasswordMustDifferFromCurrentPassword();
        }

        updatePasswordAndRevokeSessions(
                user,
                changePasswordRequest.getNewPassword(),
                OffsetDateTime.now(ZoneOffset.UTC),
                PASSWORD_CHANGED_REVOKE_REASON
        );

        return PASSWORD_CHANGED_SUCCESS_MESSAGE;
    }
}
