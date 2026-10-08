package com.pawpasta.glowscan_be.skinanalysis.application;

import com.pawpasta.glowscan_be.gemini.config.GeminiProperties;
import com.pawpasta.glowscan_be.gemini.dto.request.GeminiAnalysisRequest;
import com.pawpasta.glowscan_be.gemini.dto.response.GeminiAnalysisResponse;
import com.pawpasta.glowscan_be.shared.handler.ApiExceptionFactory;
import com.pawpasta.glowscan_be.skinanalysis.domain.enums.ObservationType;
import com.pawpasta.glowscan_be.skinanalysis.domain.enums.ObservationVisibility;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeminiSkinAnalysisService {

    private static final int MAX_OBSERVATIONS = 8;
    private static final String ANALYSIS_PROMPT = """
            Analyze only observable facial skin appearance for a consumer skincare app.
            Do not diagnose diseases, prescribe treatment, infer medical conditions, or make health claims.
            Return only the requested JSON schema.

            Evaluate image quality first. If the image is blurry, too dark, contains multiple faces,
            has major occlusion, heavy makeup, or does not show enough skin, use
            INSUFFICIENT_IMAGE_QUALITY and explain the quality issues. Do not guess.

            For observations, use only the allowed observation types. Each box2d must be
            [ymin, xmin, ymax, xmax], normalized to integers between 0 and 1000. Return at most
            eight annotations. Use NOT_VISIBLE rather than inferring an unobservable feature.
            Notes must be short, factual, non-medical descriptions of visible appearance.
            """;

    private final RestClient geminiRestClient;
    private final GeminiProperties properties;
    private final ObjectMapper objectMapper;

    public GeminiAnalysisResponse analyze(GeminiAnalysisRequest request) {
        try {
            JsonNode response = geminiRestClient.post()
                    .uri("/interactions")
                    .body(createRequest(request))
                    .retrieve()
                    .body(JsonNode.class);

            if (response == null || !"completed".equals(response.path("status").asText())) {
                throw ApiExceptionFactory.serviceUnavailable("Gemini did not complete the skin analysis");
            }

            String outputText = response.path("output_text").asText();
            if (outputText.isBlank()) {
                throw ApiExceptionFactory.serviceUnavailable("Gemini returned no skin analysis result");
            }

            JsonNode output = objectMapper.readTree(outputText);
            return parseResult(output, response.path("model").asText(properties.model()));
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            log.warn("Gemini skin analysis request failed with HTTP status {}", status);
            if (status == 429) {
                throw ApiExceptionFactory.tooManyRequests("Gemini request limit has been reached");
            }
            if (status >= 500) {
                throw ApiExceptionFactory.serviceUnavailable("Gemini skin analysis service is unavailable");
            }
            throw ApiExceptionFactory.notAcceptable("Gemini rejected the skin analysis request");
        } catch (ResourceAccessException exception) {
            log.warn("Gemini skin analysis request could not reach the provider", exception);
            throw ApiExceptionFactory.serviceUnavailable("Gemini skin analysis service is unavailable");
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            log.warn("Gemini skin analysis response could not be validated", exception);
            throw ApiExceptionFactory.internalServerError("Gemini returned an invalid skin analysis result");
        }
    }

    private Map<String, Object> createRequest(GeminiAnalysisRequest request) {
        return Map.of(
                "model", properties.model(),
                "input", List.of(
                        Map.of("type", "text", "text", ANALYSIS_PROMPT),
                        Map.of(
                                "type", "image",
                                "data", Base64.getEncoder().encodeToString(request.imageBytes()),
                                "mime_type", request.mimeType()
                        )
                ),
                "response_format", Map.of(
                        "type", "text",
                        "mime_type", "application/json",
                        "schema", responseSchema()
                ),
                "generation_config", Map.of("thinking_level", "minimal")
        );
    }

    private Map<String, Object> responseSchema() {
        Map<String, Object> observation = Map.of(
                "type", "object",
                "properties", Map.of(
                        "type", Map.of("type", "string", "enum", observationTypes()),
                        "severity", Map.of("type", "integer"),
                        "confidence", Map.of("type", "number"),
                        "visibility", Map.of("type", "string", "enum", observationVisibilities()),
                        "note", Map.of("type", "string"),
                        "box2d", Map.of("type", "array", "items", Map.of("type", "integer"))
                ),
                "required", List.of("type", "severity", "confidence", "visibility", "note", "box2d")
        );

        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "status", Map.of("type", "string", "enum", List.of("ANALYZED", "INSUFFICIENT_IMAGE_QUALITY")),
                        "qualityIssues", Map.of("type", "array", "items", Map.of("type", "string")),
                        "observations", Map.of("type", "array", "items", observation),
                        "limitations", Map.of("type", "array", "items", Map.of("type", "string"))
                ),
                "required", List.of("status", "qualityIssues", "observations", "limitations")
        );
    }

    private GeminiAnalysisResponse parseResult(JsonNode output, String modelVersion) {
        String status = requiredText(output, "status");
        if (!status.equals("ANALYZED") && !status.equals("INSUFFICIENT_IMAGE_QUALITY")) {
            throw invalidResult("Unknown analysis status");
        }

        List<String> qualityIssues = stringArray(output, "qualityIssues");
        List<String> limitations = stringArray(output, "limitations");
        List<GeminiAnalysisResponse.Observation> observations = observations(output.path("observations"));
        if (status.equals("INSUFFICIENT_IMAGE_QUALITY") && observations.stream()
                .anyMatch(observation -> observation.visibility() == ObservationVisibility.CLEAR)) {
            throw invalidResult("Low-quality analysis cannot contain clear observations");
        }

        Map<String, Object> payload = objectMapper.convertValue(output, new TypeReference<LinkedHashMap<String, Object>>() { });
        return new GeminiAnalysisResponse(
                status.equals("ANALYZED"),
                qualityIssues,
                observations,
                limitations,
                payload,
                modelVersion
        );
    }

    private List<GeminiAnalysisResponse.Observation> observations(JsonNode observationsNode) {
        if (!observationsNode.isArray() || observationsNode.size() > MAX_OBSERVATIONS) {
            throw invalidResult("Observation list is invalid");
        }

        List<GeminiAnalysisResponse.Observation> observations = new ArrayList<>();
        for (JsonNode observation : observationsNode) {
            ObservationType type = enumValue(ObservationType.class, requiredText(observation, "type"));
            int severity = requiredInt(observation, "severity");
            if (severity < 0 || severity > 4) {
                throw invalidResult("Observation severity is outside the allowed range");
            }

            BigDecimal confidence = requiredDecimal(observation, "confidence");
            if (confidence.compareTo(BigDecimal.ZERO) < 0 || confidence.compareTo(BigDecimal.ONE) > 0) {
                throw invalidResult("Observation confidence is outside the allowed range");
            }

            ObservationVisibility visibility = enumValue(
                    ObservationVisibility.class,
                    requiredText(observation, "visibility")
            );
            String note = requiredText(observation, "note");
            if (note.length() > 500) {
                throw invalidResult("Observation note exceeds the allowed length");
            }

            List<Integer> box2d = integerArray(observation, "box2d");
            if (box2d.size() != 4 || box2d.stream().anyMatch(value -> value < 0 || value > 1000)
                    || box2d.get(0) > box2d.get(2) || box2d.get(1) > box2d.get(3)) {
                throw invalidResult("Observation coordinates are invalid");
            }
            observations.add(new GeminiAnalysisResponse.Observation(
                    type, (short) severity, confidence, visibility, note, List.copyOf(box2d)
            ));
        }
        return List.copyOf(observations);
    }

    private List<String> stringArray(JsonNode parent, String field) {
        JsonNode node = parent.path(field);
        if (!node.isArray()) {
            throw invalidResult(field + " must be an array");
        }
        List<String> values = new ArrayList<>();
        for (JsonNode value : node) {
            if (!value.isTextual() || value.asText().isBlank() || value.asText().length() > 500) {
                throw invalidResult(field + " contains an invalid value");
            }
            values.add(value.asText());
        }
        return List.copyOf(values);
    }

    private List<Integer> integerArray(JsonNode parent, String field) {
        JsonNode node = parent.path(field);
        if (!node.isArray()) {
            throw invalidResult(field + " must be an array");
        }
        List<Integer> values = new ArrayList<>();
        for (JsonNode value : node) {
            if (!value.canConvertToInt()) {
                throw invalidResult(field + " contains an invalid coordinate");
            }
            values.add(value.intValue());
        }
        return values;
    }

    private String requiredText(JsonNode parent, String field) {
        JsonNode node = parent.path(field);
        if (!node.isTextual() || node.asText().isBlank()) {
            throw invalidResult(field + " is required");
        }
        return node.asText();
    }

    private int requiredInt(JsonNode parent, String field) {
        JsonNode node = parent.path(field);
        if (!node.isInt()) {
            throw invalidResult(field + " must be an integer");
        }
        return node.intValue();
    }

    private BigDecimal requiredDecimal(JsonNode parent, String field) {
        JsonNode node = parent.path(field);
        if (!node.isNumber()) {
            throw invalidResult(field + " must be a number");
        }
        return node.decimalValue();
    }

    private <T extends Enum<T>> T enumValue(Class<T> enumType, String value) {
        try {
            return Enum.valueOf(enumType, value);
        } catch (IllegalArgumentException exception) {
            throw invalidResult("Unknown enum value: " + value);
        }
    }

    private List<String> observationTypes() {
        return List.of(ObservationType.values()).stream().map(Enum::name).toList();
    }

    private List<String> observationVisibilities() {
        return List.of(ObservationVisibility.values()).stream().map(Enum::name).toList();
    }

    private ResponseStatusException invalidResult(String message) {
        return ApiExceptionFactory.internalServerError(": " + message);
    }
}
