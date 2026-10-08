package com.pawpasta.glowscan_be.cloudinary.adapter;

import com.pawpasta.glowscan_be.cloudinary.config.CloudinaryProperties;
import com.pawpasta.glowscan_be.cloudinary.adapter.dto.SignedUploadIntent;
import com.pawpasta.glowscan_be.cloudinary.adapter.dto.SignedUploadRequest;
import com.pawpasta.glowscan_be.cloudinary.port.ImageStoragePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

@Component
@RequiredArgsConstructor
public class CloudinaryStorageAdapter implements ImageStoragePort {

    private static final boolean AVATAR_OVERWRITE = true;

    private final CloudinaryProperties properties;

    @Override
    public SignedUploadIntent createSignedUpload(SignedUploadRequest request) {
        return createSignedUpload(request, properties.avatar());
    }

    @Override
    public SignedUploadIntent createSignedAnalysisUpload(SignedUploadRequest request) {
        return createSignedUpload(request, properties.analysis());
    }

    @Override
    public String avatarDeliveryUrl(String publicId) {
        return deliveryUrl(publicId);
    }

    @Override
    public String analysisDeliveryUrl(String publicId) {
        return "https://res.cloudinary.com/" + properties.cloudName()
                + "/image/upload/f_jpg/" + publicId;
    }

    private SignedUploadIntent createSignedUpload(
            SignedUploadRequest request,
            CloudinaryProperties.Upload uploadProperties
    ) {
        long timestamp = Instant.now().getEpochSecond();
        List<String> sortedAllowedFormats = uploadProperties.allowedFormats().stream()
                .map(String::toLowerCase)
                .sorted(Comparator.naturalOrder())
                .toList();
        Set<String> allowedFormats = Set.copyOf(sortedAllowedFormats);

        Map<String, String> parameters = new TreeMap<>();
        parameters.put("allowed_formats", String.join(",", sortedAllowedFormats));
        parameters.put("overwrite", Boolean.toString(AVATAR_OVERWRITE));
        parameters.put("public_id", request.publicId());
        parameters.put("timestamp", Long.toString(timestamp));

        return new SignedUploadIntent(
                "https://api.cloudinary.com/v1_1/" + properties.cloudName() + "/image/upload",
                properties.cloudName(),
                properties.apiKey(),
                timestamp,
                signature(parameters),
                request.publicId(),
                AVATAR_OVERWRITE,
                allowedFormats,
                request.expiresAt()
        );
    }

    private String deliveryUrl(String publicId) {
        return "https://res.cloudinary.com/" + properties.cloudName() + "/image/upload/" + publicId;
    }

    private String signature(Map<String, String> parameters) {
        String valueToSign = parameters.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(java.util.stream.Collectors.joining("&")) + properties.apiSecret();
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-1").digest(valueToSign.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-1 is not available", exception);
        }
    }
}
