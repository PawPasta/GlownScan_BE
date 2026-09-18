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
import com.pawpasta.glowscan_be.service.JwtService;
import com.pawpasta.glowscan_be.service.OpaqueTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCrypt;
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
    private final OpaqueTokenService opaqueTokenService;
    private final EmailService emailService;
    private final JwtService jwtService;

    private void validatePassword(RegisterRequest registerRequest) {

        if (registerRequest.getPassword() == null || !registerRequest.getPassword().matches(PASSWORD_REGEX)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Password must be 6–12 characters long and include " +
                            "at least one uppercase letter and one special character.");
        }

        if (!registerRequest.getPassword().equals(registerRequest.getConfirmPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Password confirmation does not match");
        }

    }
    private String hashPassword(String password) {
        return BCrypt.hashpw(password, BCrypt.gensalt());
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email cannot be empty");
        }

        return email.trim().toLowerCase(Locale.ROOT);
    }

    private ResponseStatusException invalidCredentials() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
    }

    private boolean passwordMatches(User user, String password) {
        if (user.getPasswordHash() == null) {
            return false;
        }

        try {
            return BCrypt.checkpw(password, user.getPasswordHash());
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private void unlockIfExpired(User user, OffsetDateTime now) {
        if (user.getStatus() != UserStatus.LOCKED) {
            return;
        }

        if (user.getLockedUntil() != null && !user.getLockedUntil().isAfter(now)) {
            user.setStatus(UserStatus.ACTIVE);
            user.setLockedUntil(null);
            user.setFailedLoginCount(0);
            return;
        }

        throw new ResponseStatusException(HttpStatus.LOCKED, "Account is temporarily locked");
    }

    private void verifyAccountCanLogin(User user, OffsetDateTime now) {
        unlockIfExpired(user, now);

        if (user.getStatus() == UserStatus.PENDING_VERIFICATION) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Please verify your email before logging in");
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

    private String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " is required");
        }
        return value.strip();
    }

    private UserDevice saveUserDevice(User user, LoginRequest loginRequest, OffsetDateTime now) {
        String deviceUuid = requireText(loginRequest.getDeviceUuid(), "Device UUID");
        String deviceName = requireText(loginRequest.getDeviceName(), "Device name");

        if (loginRequest.getPlatform() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Device platform is required");
        }

        UserDevice device = userDeviceRepository.findByUserAndDeviceUuid(user, deviceUuid)
                .orElseGet(UserDevice::new);
        device.setUser(user);
        device.setDeviceUuid(deviceUuid);
        device.setDeviceName(deviceName);
        device.setPlatform(loginRequest.getPlatform());
        device.setLastActiveAt(now);

        return userDeviceRepository.save(device);
    }

    private String createRefreshToken(User user, UserDevice device, OffsetDateTime now) {
        String rawRefreshToken = opaqueTokenService.generateToken();
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setDevice(device);
        refreshToken.setTokenHash(opaqueTokenService.hashToken(rawRefreshToken));
        refreshToken.setTokenFamilyId(UUID.randomUUID());
        refreshToken.setExpiresAt(now.plus(refreshTokenTtl));
        refreshTokenRepository.save(refreshToken);

        return rawRefreshToken;
    }

    private void createVerificationToken(User user) {
        String rawToken = opaqueTokenService.generateToken();
        ActionToken verificationToken = new ActionToken();
        verificationToken.setUser(user);
        verificationToken.setPurpose(ActionTokenPurpose.VERIFY_EMAIL);
        verificationToken.setTokenHash(opaqueTokenService.hashToken(rawToken));
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

        validatePassword(registerRequest);

        String email = normalizeEmail(registerRequest.getEmail());
        if (userRepository.existsByEmailAndDeletedAtIsNull(email))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already exists");

        Role defaultRole = roleRepository.findByCode(DEFAULT_ROLE_CODE)
                .orElseThrow(() -> new IllegalStateException("Default USER role is not configured"));

        User user = new User();

        user.setEmail(email);
        user.setFullName(registerRequest.getFullName());
        user.setPasswordHash(hashPassword(registerRequest.getPassword()));
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
        String tokenHash = opaqueTokenService.hashToken(verificationEmailRequest.getRawToken());
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
        if (loginRequest == null || loginRequest.getPassword() == null || loginRequest.getPassword().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email and password are required");
        }

        String email = normalizeEmail(loginRequest.getEmail());
        User user = userRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(this::invalidCredentials);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        verifyAccountCanLogin(user, now);

        if (!passwordMatches(user, loginRequest.getPassword())) {
            recordFailedLogin(user, now);
            userRepository.save(user);
            throw invalidCredentials();
        }

        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(now);
        userRepository.save(user);

        UserDevice device = saveUserDevice(user, loginRequest, now);
        String refreshToken = createRefreshToken(user, device, now);
        return new LoginResponse(jwtService.generateToken(user), refreshToken);
    }

    @Override
    public String logout(LogoutRequest logoutRequest) {
        return "";
    }

    @Override
    public String refreshToken(RefreshTokenRequest refreshTokenRequest) {
        return "";
    }
}
