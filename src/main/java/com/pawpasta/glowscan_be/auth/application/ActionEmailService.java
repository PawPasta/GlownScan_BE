package com.pawpasta.glowscan_be.auth.application;


import com.pawpasta.glowscan_be.auth.domain.enums.ActionTokenPurpose;
import com.pawpasta.glowscan_be.email.EmailRenderer;
import com.pawpasta.glowscan_be.email.EmailSender;
import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class ActionEmailService {

    @Value("${app.mail.links.verify}")
    String verificationEmailBaseUrl;

    @Value("${app.mail.links.resetpsw}")
    String resetPasswordBaseUrl;

    @Value("${app.mail.action-template}")
    String ACTION_TEMPLATE;

    @Value("${app.mail.footer.notes}")
    String FOOTER_NOTE ;

    private final EmailSender emailSender;
    private final EmailRenderer emailRenderer;

    private record ActionEmail(
            String subject,
            String preheader,
            String heading,
            String message,
            String actionLabel,
            String securityNote
    ) {
    }
    private String displayName(String recipientName) {
        return recipientName == null || recipientName.isBlank() ? "there" : recipientName.strip();
    }

    private String requireActionUrl(String actionUrl) {
        if (actionUrl == null || actionUrl.isBlank()) {
            throw new IllegalArgumentException("Action URL is required");
        }
        return actionUrl.strip();
    }

    private record EmailContent(String subject, String html) {
    }

    private String buildActionUrl(com.pawpasta.glowscan_be.email.EmailContent emailRequest) {
        ActionTokenPurpose purpose = Objects.requireNonNull(
                emailRequest.getActionTokenPurpose(),
                "Action token purpose is required"
        );
        String actionUrlBase = switch (purpose) {
            case VERIFY_EMAIL -> verificationEmailBaseUrl;
            case RESET_PASSWORD -> resetPasswordBaseUrl;
        };
        String tokenParameter = purpose == ActionTokenPurpose.RESET_PASSWORD
                ? "resetPasswordToken"
                : "rawToken";

        return UriComponentsBuilder.fromUriString(requireActionUrl(actionUrlBase))
                .queryParam("email", emailRequest.getRecipientEmail())
                .queryParam(tokenParameter, emailRequest.getRawToken())
                .build()
                .encode()
                .toUriString();
    }

    public void sendActionEmail(com.pawpasta.glowscan_be.email.EmailContent emailRequest) {
        try {
            String actionUrl = buildActionUrl(emailRequest);
            EmailContent email = createActionEmail(
                    emailRequest.getActionTokenPurpose(),
                    emailRequest.getRecipientName(),
                    actionUrl
            );
            emailSender.sendHtmlEmail(
                    emailRequest.getRecipientEmail(),
                    email.subject(),
                    email.html()
            );
        } catch (MessagingException | RuntimeException exception) {
            String recipient = emailRequest == null ? "unknown recipient" : emailRequest.getRecipientEmail();
            log.error("Unable to send account action email for {}", recipient, exception);
        }
    }

    private EmailContent createActionEmail(
            ActionTokenPurpose purpose,
            String recipientName,
            String actionUrl
    ) {
        ActionEmailService.ActionEmail action = switch (Objects.requireNonNull(purpose, "Action token purpose is required")) {
            case VERIFY_EMAIL -> new ActionEmailService.ActionEmail(
                    "Verify your GlowScan email",
                    "Confirm your email address to activate your GlowScan account.",
                    "Verify your email address",
                    "Thanks for joining GlowScan. Confirm your email address to activate your account.",
                    "Verify email",
                    "If you did not create a GlowScan account, you can safely ignore this email."
            );
            case RESET_PASSWORD -> new ActionEmailService.ActionEmail(
                    "Reset your GlowScan password",
                    "Use this secure link to set a new GlowScan password.",
                    "Reset your password",
                    "We received a request to reset your GlowScan password. Use the button below to choose a new one.",
                    "Reset password",
                    "If you did not request a password reset, you can safely ignore this email. Your password will remain unchanged."
            );
        };

        String html = emailRenderer.renderHtmlTemplate(ACTION_TEMPLATE, Map.of(
                "title", action.subject(),
                "preheader", action.preheader(),
                "heading", action.heading(),
                "greeting", "Hi " + displayName(recipientName) + ",",
                "message", action.message(),
                "actionLabel", action.actionLabel(),
                "actionUrl", requireActionUrl(actionUrl),
                "securityNote", action.securityNote(),
                "footerNote", FOOTER_NOTE
        ));
        return new EmailContent(action.subject(), html);
    }
}
