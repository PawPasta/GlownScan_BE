package com.pawpasta.glowscan_be.email;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class EmailRenderer {

    private final TemplateEngine templateEngine;

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
}
