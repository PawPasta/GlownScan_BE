package com.pawpasta.glowscan_be.implement;

import com.pawpasta.glowscan_be.modal.dto.request.*;
import com.pawpasta.glowscan_be.modal.dto.response.LoginResponse;
import com.pawpasta.glowscan_be.modal.entity.ActionToken;
import com.pawpasta.glowscan_be.modal.entity.Role;
import com.pawpasta.glowscan_be.modal.entity.User;
import com.pawpasta.glowscan_be.modal.entity.UserRole;
import com.pawpasta.glowscan_be.modal.entity.enums.ActionTokenPurpose;
import com.pawpasta.glowscan_be.modal.entity.enums.UserStatus;
import com.pawpasta.glowscan_be.repository.ActionTokenRepository;
import com.pawpasta.glowscan_be.repository.RoleRepository;
import com.pawpasta.glowscan_be.repository.UserRepository;
import com.pawpasta.glowscan_be.repository.UserRoleRepository;
import com.pawpasta.glowscan_be.service.AuthService;
import com.pawpasta.glowscan_be.service.EmailService;
import com.pawpasta.glowscan_be.util.OpaqueTokenUtil;
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

@Service
@RequiredArgsConstructor
public class AuthServiceImplement implements AuthService {

    //Các Tài Khoản khi tạo ở Register Mặc Định Là USER
    private static final String DEFAULT_ROLE_CODE = "USER";

    //Này là Logic Nghiệp Vụ Của Mật Khẩu khi khởi tạo, yêu cầu đầy đủ 6 - 12 kí tự, có kí tự hoa Và Đặc Biệt
    private static final String PASSWORD_REGEX = "^(?=.*[A-Z])(?=.*[\\p{P}\\p{S}]).{6,12}$";

    @Value("${app.tokens.verify-email-ttl}")
    Duration verifyEmailTokenTtl;

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final ActionTokenRepository actionTokenRepository;
    private final OpaqueTokenUtil opaqueTokenUtil;
    private final EmailService emailService;

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
    private void scheduleVerificationEmailAfterCommit(EmailContentRequest emailRequest) {
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

        String rawToken = opaqueTokenUtil.generateToken();
        ActionToken verificationToken = new ActionToken();
        verificationToken.setUser(user);
        verificationToken.setPurpose(ActionTokenPurpose.VERIFY_EMAIL);
        verificationToken.setTokenHash(opaqueTokenUtil.hashToken(rawToken));
        verificationToken.setExpiresAt(OffsetDateTime.now(ZoneOffset.UTC).plus(verifyEmailTokenTtl));
        actionTokenRepository.save(verificationToken);

        scheduleVerificationEmailAfterCommit(new EmailContentRequest(
                user.getEmail(), user.getFullName(), rawToken, ActionTokenPurpose.VERIFY_EMAIL
        ));

        return "Registration successful";
    }

    @Override
    @Transactional
    public String verifyEmailToken(VerificationEmailRequest verificationEmailRequest) {
        String email = normalizeEmail(verificationEmailRequest.getEmail());
        String tokenHash = opaqueTokenUtil.hashToken(verificationEmailRequest.getRawToken());
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
    public LoginResponse login(LoginRequest loginRequest) {
        return null;
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
