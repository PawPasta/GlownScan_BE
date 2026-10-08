package com.pawpasta.glowscan_be.skinanalysis.application;

import com.pawpasta.glowscan_be.cloudinary.port.ImageStoragePort;
import com.pawpasta.glowscan_be.gemini.dto.request.GeminiAnalysisRequest;
import com.pawpasta.glowscan_be.gemini.dto.response.GeminiAnalysisResponse;
import com.pawpasta.glowscan_be.skinanalysis.domain.AnalysisImage;
import com.pawpasta.glowscan_be.skinanalysis.domain.AnalysisObservation;
import com.pawpasta.glowscan_be.skinanalysis.domain.AnalysisOutboxEvent;
import com.pawpasta.glowscan_be.skinanalysis.domain.AnalysisResult;
import com.pawpasta.glowscan_be.skinanalysis.domain.AnalysisSession;
import com.pawpasta.glowscan_be.skinanalysis.domain.enums.AnalysisImageView;
import com.pawpasta.glowscan_be.skinanalysis.domain.enums.AnalysisOutboxStatus;
import com.pawpasta.glowscan_be.skinanalysis.domain.enums.AnalysisStatus;
import com.pawpasta.glowscan_be.skinanalysis.infrastructure.repository.AnalysisImageRepository;
import com.pawpasta.glowscan_be.skinanalysis.infrastructure.repository.AnalysisObservationRepository;
import com.pawpasta.glowscan_be.skinanalysis.infrastructure.repository.AnalysisOutboxEventRepository;
import com.pawpasta.glowscan_be.skinanalysis.infrastructure.repository.AnalysisResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class SkinAnalysisProcessingService {

    private static final int MAX_ATTEMPTS = 3;
    private static final RestClient IMAGE_REST_CLIENT = RestClient.create();

    private final AnalysisOutboxEventRepository outboxEventRepository;
    private final AnalysisImageRepository imageRepository;
    private final AnalysisResultRepository resultRepository;
    private final AnalysisObservationRepository observationRepository;
    private final ImageStoragePort imageStoragePort;
    private final GeminiSkinAnalysisService geminiSkinAnalysisService;

    @Transactional
    public void process(UUID outboxEventId) {
        AnalysisOutboxEvent event = outboxEventRepository.findById(outboxEventId).orElse(null);
        if (event == null || event.getStatus() != AnalysisOutboxStatus.PROCESSING) {
            return;
        }

        try {
            String storagePublicId = storagePublicId(event.getPayload());
            DownloadedImage image = downloadImage(storagePublicId);
            GeminiAnalysisResponse providerResult = geminiSkinAnalysisService.analyze(
                    new GeminiAnalysisRequest(image.bytes(), "image/jpeg")
            );

            persistSuccessfulResult(event, storagePublicId, image, providerResult);
        } catch (ResponseStatusException exception) {
            log.warn(
                    "Skin analysis provider failed for session {} with status {}",
                    event.getSession().getId(),
                    exception.getStatusCode().value()
            );
            handleFailure(event, isRetryable(exception), providerErrorCode(exception));
        } catch (Exception exception) {
            log.warn("Skin analysis worker failed for session {}", event.getSession().getId(), exception);
            handleFailure(event, true, "SKIN_ANALYSIS_PROCESSING_ERROR");
        }
    }

    private void persistSuccessfulResult(
            AnalysisOutboxEvent event,
            String storagePublicId,
            DownloadedImage downloadedImage,
            GeminiAnalysisResponse providerResult
    ) {
        AnalysisSession session = event.getSession();
        persistImageIfAbsent(session, storagePublicId, downloadedImage);

        AnalysisResult result = new AnalysisResult();
        result.setSession(session);
        result.setQualityAccepted(providerResult.qualityAccepted());
        result.setQualityIssues(providerResult.qualityIssues());
        result.setLimitations(providerResult.limitations());
        result.setProviderPayload(providerResult.providerPayload());
        resultRepository.save(result);

        List<AnalysisObservation> observations = IntStream.range(0, providerResult.observations().size())
                .mapToObj(index -> toObservation(
                        session,
                        providerResult.observations().get(index),
                        (short) index
                ))
                .toList();
        observationRepository.saveAll(observations);

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        session.setStatus(AnalysisStatus.SUCCEEDED);
        session.setModelVersion(providerResult.modelVersion());
        session.setCompletedAt(now);
        session.setErrorCode(null);
        session.setErrorMessage(null);
        event.setStatus(AnalysisOutboxStatus.PUBLISHED);
        event.setProcessedAt(now);
        event.setLockedAt(null);
    }

    private void persistImageIfAbsent(AnalysisSession session, String storagePublicId, DownloadedImage downloadedImage) {
        if (imageRepository.findBySession_IdAndImageView(session.getId(), AnalysisImageView.FRONT).isPresent()) {
            return;
        }
        AnalysisImage image = new AnalysisImage();
        image.setSession(session);
        image.setImageView(AnalysisImageView.FRONT);
        image.setStoragePublicId(storagePublicId);
        image.setMimeType("image/jpeg");
        image.setByteSize((long) downloadedImage.bytes().length);
        image.setWidth(downloadedImage.width());
        image.setHeight(downloadedImage.height());
        image.setSha256(sha256(downloadedImage.bytes()));
        imageRepository.save(image);
    }

    private AnalysisObservation toObservation(
            AnalysisSession session,
            GeminiAnalysisResponse.Observation providerObservation,
            short displayOrder
    ) {
        AnalysisObservation observation = new AnalysisObservation();
        observation.setSession(session);
        observation.setObservationType(providerObservation.type());
        observation.setSeverity(providerObservation.severity());
        observation.setConfidence(providerObservation.confidence());
        observation.setVisibility(providerObservation.visibility());
        observation.setNote(providerObservation.note());
        observation.setBox2d(providerObservation.box2d());
        observation.setDisplayOrder(displayOrder);
        return observation;
    }

    private DownloadedImage downloadImage(String storagePublicId) {
        byte[] bytes = IMAGE_REST_CLIENT
                .get()
                .uri(imageStoragePort.analysisDeliveryUrl(storagePublicId))
                .retrieve()
                .body(byte[].class);
        if (bytes == null || bytes.length == 0) {
            throw new IllegalStateException("Analysis image could not be downloaded");
        }

        try {
            BufferedImage bufferedImage = ImageIO.read(new ByteArrayInputStream(bytes));
            if (bufferedImage == null || bufferedImage.getWidth() <= 0 || bufferedImage.getHeight() <= 0) {
                throw new IllegalStateException("Analysis image is not a valid JPEG");
            }
            return new DownloadedImage(bytes, bufferedImage.getWidth(), bufferedImage.getHeight());
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Analysis image could not be decoded", exception);
        }
    }

    private boolean isRetryable(ResponseStatusException exception) {
        return exception.getStatusCode().value() == HttpStatus.TOO_MANY_REQUESTS.value()
                || exception.getStatusCode().is5xxServerError();
    }

    private String providerErrorCode(ResponseStatusException exception) {
        if (exception.getStatusCode().value() == HttpStatus.TOO_MANY_REQUESTS.value()) {
            return "GEMINI_RATE_LIMITED";
        }
        if (exception.getStatusCode().is5xxServerError()) {
            return "GEMINI_UNAVAILABLE";
        }
        return "GEMINI_REQUEST_REJECTED";
    }

    private void handleFailure(AnalysisOutboxEvent event, boolean retryable, String errorCode) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        int attemptCount = event.getAttemptCount() + 1;
        event.setAttemptCount(attemptCount);
        event.setLockedAt(null);

        AnalysisSession session = event.getSession();
        session.setAttemptCount(attemptCount);

        if (retryable && attemptCount < MAX_ATTEMPTS) {
            OffsetDateTime nextAttemptAt = now.plusMinutes(attemptCount);
            event.setStatus(AnalysisOutboxStatus.PENDING);
            event.setAvailableAt(nextAttemptAt);
            session.setStatus(AnalysisStatus.QUEUED);
            session.setNextAttemptAt(nextAttemptAt);
            session.setErrorCode(null);
            session.setErrorMessage(null);
            return;
        }

        event.setStatus(AnalysisOutboxStatus.FAILED);
        event.setProcessedAt(now);
        session.setStatus(AnalysisStatus.FAILED);
        session.setCompletedAt(now);
        session.setNextAttemptAt(null);
        session.setErrorCode(errorCode);
        session.setErrorMessage("Skin analysis could not be completed. Please try again later.");
    }

    private String storagePublicId(Map<String, Object> payload) {
        Object publicId = payload.get("storagePublicId");
        if (!(publicId instanceof String value) || value.isBlank()) {
            throw new IllegalStateException("Analysis outbox event does not contain a storage public id");
        }
        return value;
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private record DownloadedImage(byte[] bytes, int width, int height) {
    }
}
