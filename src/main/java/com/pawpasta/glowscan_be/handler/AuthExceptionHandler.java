package com.pawpasta.glowscan_be.handler;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static com.pawpasta.glowscan_be.handler.ExceptionHandler.*;

/**
 * Named auth exceptions exposed to API clients.
 *
 * <p>Client-facing messages stay private and are only exposed through the
 * factory method that represents the corresponding business error.</p>
 */
public final class AuthExceptionHandler {

    private static final String EMAIL_CANNOT_BE_EMPTY_MESSAGE = "Email cannot be empty";
    private static final String PASSWORD_POLICY_INVALID_MESSAGE =
            "Password must be 6–12 characters long and include at least one uppercase letter and one special character.";
    private static final String PASSWORD_CONFIRMATION_DOES_NOT_MATCH_MESSAGE = "Password confirmation does not match";
    private static final String ACCOUNT_TEMPORARILY_LOCKED_MESSAGE = "Account is temporarily locked";
    private static final String ACCOUNT_CANNOT_LOG_IN_MESSAGE = "Account is not allowed to log in";
    private static final String DEVICE_INFORMATION_REQUIRED_MESSAGE = "Device information is required";
    private static final String DEVICE_PLATFORM_REQUIRED_MESSAGE = "Device platform is required";
    private static final String REGISTER_REQUEST_REQUIRED_MESSAGE = "Register request is required";
    private static final String EMAIL_ALREADY_EXISTS_MESSAGE = "Email already exists";
    private static final String DEFAULT_USER_ROLE_NOT_CONFIGURED_MESSAGE = "Default USER role is not configured";
    private static final String VERIFICATION_EMAIL_REQUEST_REQUIRED_MESSAGE = "Verification email request is required";
    private static final String VERIFICATION_LINK_INVALID_OR_EXPIRED_MESSAGE = "Verification link is invalid or expired";
    private static final String USER_NOT_PENDING_VERIFICATION_MESSAGE = "User is not pending verification";
    private static final String EMAIL_AND_PASSWORD_REQUIRED_MESSAGE = "Email and password are required";
    private static final String INVALID_CREDENTIALS_MESSAGE = "Invalid email or password. Try again.";
    private static final String ACCESS_TOKEN_REQUIRED_MESSAGE = "Access token is required";
    private static final String ACCESS_TOKEN_INVALID_OR_EXPIRED_MESSAGE = "Access token is invalid or expired";
    private static final String ACCESS_AND_REFRESH_TOKEN_REQUIRED_MESSAGE = "Access token and refresh token are required";
    private static final String REFRESH_TOKEN_INVALID_OR_EXPIRED_MESSAGE = "Refresh token is invalid or expired";
    private static final String RESET_PASSWORD_REQUEST_REQUIRED_MESSAGE = "Reset password request is required";
    private static final String EMAIL_NOT_FOUND_MESSAGE = "Email was not found";
    private static final String ACCOUNT_CANNOT_RESET_PASSWORD_MESSAGE = "Only active accounts can reset their password";
    private static final String PASSWORD_RESET_LINK_INVALID_OR_EXPIRED_MESSAGE = "Password reset link is invalid or expired";
    private static final String CHANGE_PASSWORD_REQUEST_REQUIRED_MESSAGE = "Change password request is required";
    private static final String USER_NOT_AUTHORIZED_MESSAGE = "User is not authorized";
    private static final String ACCOUNT_CANNOT_CHANGE_PASSWORD_MESSAGE = "Account is not allowed to change password";
    private static final String CURRENT_PASSWORD_INCORRECT_MESSAGE = "Current password is incorrect";
    private static final String NEW_PASSWORD_MUST_DIFFER_FROM_CURRENT_PASSWORD_MESSAGE =
            "New password must be different from the current password";

    private AuthExceptionHandler() {
    }

    public static ResponseStatusException emailCannotBeEmpty() {
        return badRequest(EMAIL_CANNOT_BE_EMPTY_MESSAGE);
    }

    public static ResponseStatusException passwordPolicyInvalid() {
        return badRequest(PASSWORD_POLICY_INVALID_MESSAGE);
    }

    public static ResponseStatusException passwordConfirmationDoesNotMatch() {
        return badRequest(PASSWORD_CONFIRMATION_DOES_NOT_MATCH_MESSAGE);
    }

    public static ResponseStatusException accountTemporarilyLocked() {
        return new ResponseStatusException(HttpStatus.LOCKED, ACCOUNT_TEMPORARILY_LOCKED_MESSAGE);
    }

    public static ResponseStatusException accountCannotLogIn() {
        return forbidden(ACCOUNT_CANNOT_LOG_IN_MESSAGE);
    }

    public static ResponseStatusException deviceInformationRequired() {
        return badRequest(DEVICE_INFORMATION_REQUIRED_MESSAGE);
    }

    public static ResponseStatusException devicePlatformRequired() {
        return badRequest(DEVICE_PLATFORM_REQUIRED_MESSAGE);
    }

    public static ResponseStatusException registerRequestRequired() {
        return badRequest(REGISTER_REQUEST_REQUIRED_MESSAGE);
    }

    public static ResponseStatusException emailAlreadyExists() {
        return new ResponseStatusException(HttpStatus.CONFLICT, EMAIL_ALREADY_EXISTS_MESSAGE);
    }

    public static IllegalStateException defaultUserRoleNotConfigured() {
        return new IllegalStateException(DEFAULT_USER_ROLE_NOT_CONFIGURED_MESSAGE);
    }

    public static ResponseStatusException verificationEmailRequestRequired() {
        return badRequest(VERIFICATION_EMAIL_REQUEST_REQUIRED_MESSAGE);
    }

    public static ResponseStatusException verificationLinkInvalidOrExpired() {
        return badRequest(VERIFICATION_LINK_INVALID_OR_EXPIRED_MESSAGE);
    }

    public static ResponseStatusException userNotPendingVerification() {
        return new ResponseStatusException(HttpStatus.CONFLICT, USER_NOT_PENDING_VERIFICATION_MESSAGE);
    }

    public static ResponseStatusException emailAndPasswordRequired() {
        return badRequest(EMAIL_AND_PASSWORD_REQUIRED_MESSAGE);
    }

    public static ResponseStatusException invalidCredentials() {
        return unauthorized(INVALID_CREDENTIALS_MESSAGE);
    }

    public static ResponseStatusException accessTokenRequired() {
        return badRequest(ACCESS_TOKEN_REQUIRED_MESSAGE);
    }

    public static ResponseStatusException accessTokenInvalidOrExpired() {
        return unauthorized(ACCESS_TOKEN_INVALID_OR_EXPIRED_MESSAGE);
    }

    public static ResponseStatusException accessAndRefreshTokenRequired() {
        return badRequest(ACCESS_AND_REFRESH_TOKEN_REQUIRED_MESSAGE);
    }

    public static ResponseStatusException refreshTokenInvalidOrExpired() {
        return unauthorized(REFRESH_TOKEN_INVALID_OR_EXPIRED_MESSAGE);
    }

    public static ResponseStatusException resetPasswordRequestRequired() {
        return badRequest(RESET_PASSWORD_REQUEST_REQUIRED_MESSAGE);
    }

    public static ResponseStatusException emailNotFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, EMAIL_NOT_FOUND_MESSAGE);
    }

    public static ResponseStatusException accountCannotResetPassword() {
        return forbidden(ACCOUNT_CANNOT_RESET_PASSWORD_MESSAGE);
    }

    public static ResponseStatusException passwordResetLinkInvalidOrExpired() {
        return badRequest(PASSWORD_RESET_LINK_INVALID_OR_EXPIRED_MESSAGE);
    }

    public static ResponseStatusException changePasswordRequestRequired() {
        return badRequest(CHANGE_PASSWORD_REQUEST_REQUIRED_MESSAGE);
    }

    public static ResponseStatusException userNotAuthorized() {
        return unauthorized(USER_NOT_AUTHORIZED_MESSAGE);
    }

    public static ResponseStatusException accountCannotChangePassword() {
        return forbidden(ACCOUNT_CANNOT_CHANGE_PASSWORD_MESSAGE);
    }

    public static ResponseStatusException currentPasswordIncorrect() {
        return badRequest(CURRENT_PASSWORD_INCORRECT_MESSAGE);
    }

    public static ResponseStatusException newPasswordMustDifferFromCurrentPassword() {
        return badRequest(NEW_PASSWORD_MUST_DIFFER_FROM_CURRENT_PASSWORD_MESSAGE);
    }

}
