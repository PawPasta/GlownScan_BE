package com.pawpasta.glowscan_be.util;

import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class EmailUtil {

    private final TemplateEngine templateEngine;
    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    String mailFrom;

    public String renderHtmlTemplate(String templateName, Map<String, Object> variables) {
        if (templateName == null || templateName.isBlank()) {
            throw new IllegalArgumentException("Template name is required");
        }
        if (variables == null) {
            throw new IllegalArgumentException("Template variables are required");
        }

        Context context = new Context();
        context.setVariables(variables);
        return templateEngine.process(templateName.strip(), context);
    }

    public void sendHtmlEmail(String recipientEmail, String subject, String html) throws MessagingException {
        requireText(recipientEmail, "Recipient email");
        requireText(subject, "Email subject");
        requireText(html, "Email HTML");

        var mimeMessage = mailSender.createMimeMessage();
        var helper = new MimeMessageHelper(mimeMessage, false, StandardCharsets.UTF_8.name());
        helper.setFrom(mailFrom);
        helper.setTo(recipientEmail);
        helper.setSubject(subject);
        helper.setText(html, true);
        mailSender.send(mimeMessage);
    }

    private void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
    }
}
