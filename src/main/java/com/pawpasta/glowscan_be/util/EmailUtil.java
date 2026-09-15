package com.pawpasta.glowscan_be.util;

import com.pawpasta.glowscan_be.modal.dto.request.VerificationEmailRequest;
import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Slf4j
@RequiredArgsConstructor
public class EmailUtil {

    private static final String ACTION_TEMPLATE_PATH = "templates/email/account-action.html";
    private static final Pattern TEMPLATE_TOKEN = Pattern.compile("\\{\\{([a-z_]+)\\}\\}");

    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    String fromAddress;

    @Value("${app.links.verify-email}")
    String verificationEmailBaseUrl;

    public EmailContent createVerificationEmail(String recipientName, String verificationUrl) {
        return createActionEmail(
                "Verify your GlowScan email",
                "Confirm your email address to activate your GlowScan account.",
                "Verify your email address",
                recipientName,
                "Thanks for joining GlowScan. Confirm your email address to activate your account.",
                "Verify email",
                verificationUrl,
                "If you did not create a GlowScan account, you can safely ignore this email."
        );
    }

    public EmailContent createPasswordResetEmail(String recipientName, String resetPasswordUrl) {
        return createActionEmail(
                "Reset your GlowScan password",
                "Use this secure link to set a new GlowScan password.",
                "Reset your password",
                recipientName,
                "We received a request to reset your GlowScan password. Use the button below to choose a new one.",
                "Reset password",
                resetPasswordUrl,
                "If you did not request a password reset, you can safely ignore this email. Your password will remain unchanged."
        );
    }

    public void sendVerificationEmail(VerificationEmailRequest emailRequest) {
        try {
            String verificationUrl = buildVerificationUrl(emailRequest);
            EmailContent email = createVerificationEmail(emailRequest.getRecipientName(), verificationUrl);
            sendHtmlEmail(emailRequest.getRecipientEmail(), email);
        } catch (MessagingException | RuntimeException exception) {
            log.error("Unable to send verification email for {}", emailRequest.getRecipientEmail(), exception);
        }
    }

    private EmailContent createActionEmail(
            String subject,
            String preheader,
            String heading,
            String recipientName,
            String message,
            String actionLabel,
            String actionUrl,
            String securityNote
    ) {
        String html = renderTemplate(Map.of(
                "title", subject,
                "preheader", preheader,
                "heading", heading,
                "greeting", "Hi " + displayName(recipientName) + ",",
                "message", message,
                "action_label", actionLabel,
                "action_url", requireActionUrl(actionUrl),
                "security_note", securityNote,
                "footer_note", "GlowScan · Your personal skin companion"
        ));
        return new EmailContent(subject, html);
    }

    private String renderTemplate(Map<String, String> values) {
        String template = loadTemplate();
        Matcher matcher = TEMPLATE_TOKEN.matcher(template);
        StringBuffer rendered = new StringBuffer();

        while (matcher.find()) {
            String tokenName = matcher.group(1);
            String value = values.get(tokenName);
            if (value == null) {
                throw new IllegalStateException("No value was provided for email template token: " + tokenName);
            }
            matcher.appendReplacement(rendered, Matcher.quoteReplacement(escapeHtml(value)));
        }
        matcher.appendTail(rendered);
        return rendered.toString();
    }

    private String buildVerificationUrl(VerificationEmailRequest emailRequest) {
        return UriComponentsBuilder.fromUriString(verificationEmailBaseUrl)
                .queryParam("email", emailRequest.getRecipientEmail())
                .queryParam("token", emailRequest.getRawToken())
                .build()
                .encode()
                .toUriString();
    }

    private void sendHtmlEmail(String recipientEmail, EmailContent email) throws MessagingException {
        var mimeMessage = mailSender.createMimeMessage();
        var helper = new MimeMessageHelper(mimeMessage, false, StandardCharsets.UTF_8.name());
        helper.setFrom(fromAddress);
        helper.setTo(recipientEmail);
        helper.setSubject(email.subject());
        helper.setText(email.html(), true);
        mailSender.send(mimeMessage);
    }

    private String loadTemplate() {
        try (var inputStream = new ClassPathResource(ACTION_TEMPLATE_PATH).getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load email template", exception);
        }
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

    private String escapeHtml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    public record EmailContent(String subject, String html) {
    }
}
