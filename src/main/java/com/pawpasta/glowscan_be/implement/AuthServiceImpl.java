package com.pawpasta.glowscan_be.implement;

import com.pawpasta.glowscan_be.modal.dto.request.*;
import com.pawpasta.glowscan_be.modal.dto.response.LoginResponse;
import com.pawpasta.glowscan_be.modal.entity.ActionToken;
import com.pawpasta.glowscan_be.modal.entity.RefreshToken;
import com.pawpasta.glowscan_be.modal.entity.Role;
import com.pawpasta.glowscan_be.modal.entity.User;
import com.pawpasta.glowscan_be.modal.entity.UserDevice;
import com.pawpasta.glowscan_be.modal.entity.UserRole;
import com.pawpasta.glowscan_be.modal.entity.enums.ActionTokenPurpose;
import com.pawpasta.glowscan_be.modal.entity.enums.UserStatus;
import com.pawpasta.glowscan_be.repository.ActionTokenRepository;
import com.pawpasta.glowscan_be.repository.RefreshTokenRepository;
import com.pawpasta.glowscan_be.repository.RoleRepository;
import com.pawpasta.glowscan_be.repository.UserDeviceRepository;
import com.pawpasta.glowscan_be.repository.UserRepository;
import com.pawpasta.glowscan_be.repository.UserRoleRepository;
import com.pawpasta.glowscan_be.service.AuthService;
import com.pawpasta.glowscan_be.service.EmailService;
import com.pawpasta.glowscan_be.service.TokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
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

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    @Value("${app.max.failed.login}")
    private int MAX_FAILED_LOGIN_ATTEMPTS;

    @Value("${app.login.lock}")
    private Duration LOGIN_LOCK_DURATION;

    //Các Tài Khoản khi tạo ở Register Mặc Định Là USER
    private static final String DEFAULT_ROLE_CODE = "USER";

    //Này là Logic Nghiệp Vụ Của Mật Khẩu khi khởi tạo, yêu cầu đầy đủ 6 - 12 kí tự, có kí tự hoa Và Đặc Biệt
    private static final String PASSWORD_REGEX = "^(?=.*[A-Z])(?=.*[\\p{P}\\p{S}]).{6,12}$";

    @Value("${app.tokens.verify-email-ttl}")
    Duration verifyEmailTokenTtl;

    @Value("${app.tokens.refresh-token-ttl}")
    Duration refreshTokenTtl;

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final ActionTokenRepository actionTokenRepository;
    private final UserDeviceRepository userDeviceRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final TokenService tokenService;
    private final EmailService emailService;

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email cannot be empty");
        }

        return email.trim().toLowerCase(Locale.ROOT);
    }

    private void verifyAccountCanLogin(User user, OffsetDateTime now) {
        if (user.getStatus() == UserStatus.LOCKED) {
            if (user.getLockedUntil() != null && !user.getLockedUntil().isAfter(now)) {
                user.setStatus(UserStatus.ACTIVE);
                user.setLockedUntil(null);
                user.setFailedLoginCount(0);
            } else {
                throw new ResponseStatusException(HttpStatus.LOCKED, "Account is temporarily locked");
            }
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account is not allowed to log in");
        }
    }

    private void recordFailedLogin(User user, OffsetDateTime now) {
        int failedAttempts = (user.getFailedLoginCount() == null ? 0 : user.getFailedLoginCount()) + 1;
        user.setFailedLoginCount(failedAttempts);

        if (failedAttempts >= MAX_FAILED_LOGIN_ATTEMPTS) {
            user.setStatus(UserStatus.LOCKED);
            user.setLockedUntil(now.plus(LOGIN_LOCK_DURATION));
        }
    }

    private UserDevice saveUserDevice(User user, LoginRequest loginRequest, OffsetDateTime now) {

        if ( loginRequest.getDeviceUuid() == null || loginRequest.getDeviceUuid().isBlank() ||
                loginRequest.getDeviceName() == null || loginRequest.getDeviceName().isBlank()
        ) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,  "Device Information is required");
        }

        if (loginRequest.getPlatform() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Device platform is required");
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

    private void createVerificationToken(User user) {
        String rawToken = tokenService.generateActionToken();
        ActionToken verificationToken = new ActionToken();
        verificationToken.setUser(user);
        verificationToken.setPurpose(ActionTokenPurpose.VERIFY_EMAIL);
        verificationToken.setTokenHash(tokenService.hashActionToken(rawToken));
        verificationToken.setExpiresAt(OffsetDateTime.now(ZoneOffset.UTC).plus(verifyEmailTokenTtl));
        actionTokenRepository.save(verificationToken);

        scheduleEmailAfterCommit(new EmailContentRequest(
                user.getEmail(), user.getFullName(), rawToken, ActionTokenPurpose.VERIFY_EMAIL
        ));
    }

    private void scheduleEmailAfterCommit(EmailContentRequest emailRequest) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                emailService.sendActionEmail(emailRequest);
            }
        });
    }

    @Override
    @Transactional
    public String register(RegisterRequest registerRequest) {

        if (registerRequest == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Register request is required");
        }

        if (registerRequest.getPassword() == null || !registerRequest.getPassword().matches(PASSWORD_REGEX)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Password must be 6–12 characters long and include " +
                            "at least one uppercase letter and one special character.");
        }

        if (!registerRequest.getPassword().equals(registerRequest.getConfirmPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Password confirmation does not match");
        }

        String email = normalizeEmail(registerRequest.getEmail());
        if (userRepository.existsByEmailAndDeletedAtIsNull(email))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already exists");

        Role defaultRole = roleRepository.findByCode(DEFAULT_ROLE_CODE)
                .orElseThrow(() -> new IllegalStateException("Default USER role is not configured"));

        User user = new User();

        user.setEmail(email);
        user.setFullName(registerRequest.getFullName());
        user.setPasswordHash(BCrypt.hashpw(registerRequest.getPassword(), BCrypt.gensalt()));
        user.setStatus(UserStatus.PENDING_VERIFICATION);
        user.setTokenVersion(1);
        user.setFailedLoginCount(0);

        userRepository.save(user);

        UserRole userRole = new UserRole();

        userRole.setUser(user);
        userRole.setRole(defaultRole);

        userRoleRepository.save(userRole);

        createVerificationToken(user);

        return "Registration successful";
    }

    @Override
    @Transactional
    public String verifyEmailToken(VerificationEmailRequest verificationEmailRequest) {
        String email = normalizeEmail(verificationEmailRequest.getEmail());
        String tokenHash = tokenService.hashActionToken(verificationEmailRequest.getRawToken());
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        ActionToken verificationToken = actionTokenRepository
                .findPendingByUserEmailAndTokenHashAndPurpose(
                        email,
                        tokenHash,
                        ActionTokenPurpose.VERIFY_EMAIL
                )
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Verification link is invalid or expired"
                ));

        if (verificationToken.getExpiresAt() == null || !verificationToken.getExpiresAt().isAfter(now)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Verification link is invalid or expired");
        }

        User user = verificationToken.getUser();
        if (user.getStatus() != UserStatus.PENDING_VERIFICATION) {
            throw new  ResponseStatusException(HttpStatus.CONFLICT, "User Are Not In Pending Status");
        }

        user.setStatus(UserStatus.ACTIVE);
        user.setEmailVerifiedAt(now);
        verificationToken.setConsumedAt(now);

        userRepository.save(user);
        actionTokenRepository.save(verificationToken);

        return "Email verified successfully";
    }


    @Override
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public LoginResponse login(LoginRequest loginRequest) {
        if (loginRequest.getEmail() == null || loginRequest.getPassword() == null || loginRequest.getPassword().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email and password are required");
        }

        User user = userRepository.findByEmailAndDeletedAtIsNull(normalizeEmail(loginRequest.getEmail()))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Invalid Email Or Password, Try Again"
                ));
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        verifyAccountCanLogin(user, now);

        boolean passwordMatches;
        try {
            passwordMatches = user.getPasswordHash() != null
                    && BCrypt.checkpw(loginRequest.getPassword(), user.getPasswordHash());
        } catch (IllegalArgumentException exception) {
            passwordMatches = false;
        }

        if (!passwordMatches) {
            recordFailedLogin(user, now);
            userRepository.save(user);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Email Or Password, Try Again");
        }

        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
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

    @Override
    @Transactional
    public String logout(LogoutRequest logoutRequest) {
        if (logoutRequest == null || logoutRequest.getAccessToken() == null
                || logoutRequest.getAccessToken().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Access token is required");
        }

        try {
            Jwt jwt = tokenService.decodeJWTToken(logoutRequest.getAccessToken().strip());
            String userIdClaim = jwt.getClaimAsString("uid");
            if (userIdClaim == null || userIdClaim.isBlank()) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Access token is invalid or expired");
            }

            refreshTokenRepository.revokeActiveTokensByUserId(
                    UUID.fromString(userIdClaim),
                    OffsetDateTime.now(ZoneOffset.UTC),
                    "LOGOUT"
            );
        } catch (JwtException | IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Access token is invalid or expired");
        }

        return "Logged out successfully";
    }

    @Override
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public LoginResponse refreshToken(RefreshTokenRequest refreshTokenRequest) {
        if (refreshTokenRequest == null || refreshTokenRequest.getAccessToken() == null
                || refreshTokenRequest.getAccessToken().isBlank()
                || refreshTokenRequest.getRefreshToken() == null
                || refreshTokenRequest.getRefreshToken().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Access token and refresh token are required");
        }

        try {
            Jwt jwt = tokenService.decodeJWTTokenForRefresh(refreshTokenRequest.getAccessToken().strip());
            String userIdClaim = jwt.getClaimAsString("uid");
            if (userIdClaim == null || userIdClaim.isBlank()) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Access token is invalid or expired");
            }
        } catch (JwtException | IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Access token is invalid or expired");
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        RefreshToken currentRefreshToken = refreshTokenRepository
                .findByTokenHash(tokenService.hashActionToken(refreshTokenRequest.getRefreshToken().strip()))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Refresh token is invalid or expired"
                ));

        if (currentRefreshToken.getUser() == null || currentRefreshToken.getUser().getId() == null
                || currentRefreshToken.getTokenFamilyId() == null
                || currentRefreshToken.getExpiresAt() == null
                || !currentRefreshToken.getExpiresAt().isAfter(now)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token is invalid or expired");
        }

        if (currentRefreshToken.getRevokedAt() != null) {
            refreshTokenRepository.revokeActiveTokensByFamilyId(
                    currentRefreshToken.getTokenFamilyId(),
                    now,
                    "REFRESH_TOKEN_REUSE"
            );
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token is invalid or expired");
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

    @Override
    public String resetPassword(ResetPasswordRequest resetPasswordRequest) {
        return "";
    }

    @Override
    public String verifyResetPasswordToken(VerifyResetPasswordRequest verifyResetPasswordRequest) {
        return "";
    }

    @Override
    public String changePassword(ChangePasswordRequest changePasswordRequest) {
        return "";
    }
}
