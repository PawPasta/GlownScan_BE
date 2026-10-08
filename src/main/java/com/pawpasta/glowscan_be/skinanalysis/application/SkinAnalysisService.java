package com.pawpasta.glowscan_be.skinanalysis.application;

import com.pawpasta.glowscan_be.auth.domain.User;
import com.pawpasta.glowscan_be.auth.infrastructure.repository.UserRepository;
import com.pawpasta.glowscan_be.cloudinary.config.CloudinaryProperties;
import com.pawpasta.glowscan_be.cloudinary.adapter.dto.SignedUploadIntent;
import com.pawpasta.glowscan_be.cloudinary.adapter.dto.SignedUploadRequest;
import com.pawpasta.glowscan_be.cloudinary.port.ImageStoragePort;
import com.pawpasta.glowscan_be.shared.handler.ApiExceptionFactory;
import com.pawpasta.glowscan_be.shared.security.CurrentUserProvider;
import com.pawpasta.glowscan_be.skinanalysis.application.dto.request.SubmitSkinAnalysisRequest;
import com.pawpasta.glowscan_be.skinanalysis.application.dto.response.AnalysisUploadIntentResponse;
import com.pawpasta.glowscan_be.skinanalysis.application.dto.response.SkinAnalysisResponse;
import com.pawpasta.glowscan_be.skinanalysis.application.dto.response.SkinAnalysisSubmitResponse;
import com.pawpasta.glowscan_be.skinanalysis.domain.AnalysisConsent;
import com.pawpasta.glowscan_be.skinanalysis.domain.AnalysisOutboxEvent;
import com.pawpasta.glowscan_be.skinanalysis.domain.AnalysisSession;
import com.pawpasta.glowscan_be.skinanalysis.domain.AnalysisUploadIntent;
import com.pawpasta.glowscan_be.skinanalysis.domain.AssessmentSnapshot;
import com.pawpasta.glowscan_be.skinanalysis.domain.enums.AnalysisConsentPurpose;
import com.pawpasta.glowscan_be.skinanalysis.domain.enums.AnalysisImageView;
import com.pawpasta.glowscan_be.skinanalysis.domain.enums.AnalysisOutboxEventType;
import com.pawpasta.glowscan_be.skinanalysis.domain.enums.AnalysisOutboxStatus;
import com.pawpasta.glowscan_be.skinanalysis.domain.enums.AnalysisProvider;
import com.pawpasta.glowscan_be.skinanalysis.domain.enums.AnalysisStatus;
import com.pawpasta.glowscan_be.skinanalysis.infrastructure.repository.AnalysisConsentRepository;
import com.pawpasta.glowscan_be.skinanalysis.infrastructure.repository.AnalysisImageRepository;
import com.pawpasta.glowscan_be.skinanalysis.infrastructure.repository.AnalysisObservationRepository;
import com.pawpasta.glowscan_be.skinanalysis.infrastructure.repository.AnalysisOutboxEventRepository;
import com.pawpasta.glowscan_be.skinanalysis.infrastructure.repository.AnalysisResultRepository;
import com.pawpasta.glowscan_be.skinanalysis.infrastructure.repository.AnalysisSessionRepository;
import com.pawpasta.glowscan_be.skinanalysis.infrastructure.repository.AnalysisUploadIntentRepository;
import com.pawpasta.glowscan_be.skinanalysis.infrastructure.repository.AssessmentSnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SkinAnalysisService {

    private final CurrentUserProvider currentUserProvider;
    private final UserRepository userRepository;
    private final CloudinaryProperties cloudinaryProperties;
    private final ImageStoragePort imageStoragePort;
    private final AnalysisUploadIntentRepository uploadIntentRepository;
    private final AnalysisSessionRepository sessionRepository;
    private final AssessmentSnapshotRepository assessmentSnapshotRepository;
    private final AnalysisConsentRepository consentRepository;
    private final AnalysisOutboxEventRepository outboxEventRepository;
    private final AnalysisResultRepository resultRepository;
    private final AnalysisObservationRepository observationRepository;
    private final AnalysisImageRepository imageRepository;

    @Transactional
    public AnalysisUploadIntentResponse createUploadIntent() {
        User user = lockCurrentUser();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime expiresAt = now.plus(cloudinaryProperties.analysis().uploadIntentTtl());

        uploadIntentRepository.revokePendingByUserId(user.getId(), now);

        AnalysisUploadIntent intent = new AnalysisUploadIntent();
        intent.setUser(user);
        intent.setPublicId(analysisPublicId(user));
        intent.setExpiresAt(expiresAt);
        intent = uploadIntentRepository.saveAndFlush(intent);

        SignedUploadIntent signedUpload = imageStoragePort.createSignedAnalysisUpload(
                new SignedUploadRequest(intent.getPublicId(), expiresAt)
        );
        return new AnalysisUploadIntentResponse(
                intent.getId(),
                signedUpload.uploadUrl(),
                signedUpload.cloudName(),
                signedUpload.apiKey(),
                signedUpload.timestamp(),
                signedUpload.signature(),
                signedUpload.publicId(),
                signedUpload.overwrite(),
                signedUpload.expiresAt(),
                new AnalysisUploadIntentResponse.UploadConstraints(
                        signedUpload.allowedFormats(),
                        cloudinaryProperties.analysis().maxBytes(),
                        cloudinaryProperties.analysis().maxDimension()
                )
        );
    }

    @Transactional
    public SkinAnalysisSubmitResponse submit(SubmitSkinAnalysisRequest request, String idempotencyKey) {
        User user = currentUserProvider.getUserContext();
        String normalizedIdempotencyKey = normalizeIdempotencyKey(idempotencyKey);
        if (normalizedIdempotencyKey != null) {
            var existing = sessionRepository.findByUser_IdAndIdempotencyKey(user.getId(), normalizedIdempotencyKey);
            if (existing.isPresent()) {
                return new SkinAnalysisSubmitResponse(existing.get().getId(), existing.get().getStatus());
            }
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        AnalysisUploadIntent uploadIntent = uploadIntentRepository
                .findByIdAndUserIdForUpdate(request.uploadIntentId(), user.getId())
                .orElseThrow(() -> ApiExceptionFactory.badRequest("Skin analysis upload intent is invalid or expired"));
        if (uploadIntent.getConsumedAt() != null
                || uploadIntent.getRevokedAt() != null
                || !uploadIntent.getExpiresAt().isAfter(now)) {
            throw ApiExceptionFactory.badRequest("Skin analysis upload intent is invalid or expired");
        }

        AnalysisSession session = new AnalysisSession();
        session.setUser(user);
        session.setStatus(AnalysisStatus.QUEUED);
        session.setAnalysisKind(request.analysisKind());
        session.setProvider(AnalysisProvider.GEMINI);
        session.setPromptVersion("skin-analysis-v1");
        session.setResultSchemaVersion("v1");
        session.setRulesVersion("v1");
        session.setIdempotencyKey(normalizedIdempotencyKey);
        session = sessionRepository.saveAndFlush(session);

        AssessmentSnapshot assessment = new AssessmentSnapshot();
        assessment.setSession(session);
        assessment.setQuestionnaireVersion(request.assessment().questionnaireVersion().strip());
        assessment.setAnswers(request.assessment().answers());
        assessmentSnapshotRepository.save(assessment);

        AnalysisConsent consent = new AnalysisConsent();
        consent.setUser(user);
        consent.setSession(session);
        consent.setPurpose(AnalysisConsentPurpose.SKIN_ANALYSIS);
        consent.setConsentVersion(request.consentVersion().strip());
        consentRepository.save(consent);

        AnalysisOutboxEvent outboxEvent = new AnalysisOutboxEvent();
        outboxEvent.setSession(session);
        outboxEvent.setEventType(AnalysisOutboxEventType.PROCESS_ANALYSIS);
        outboxEvent.setStatus(AnalysisOutboxStatus.PENDING);
        outboxEvent.setPayload(Map.of("storagePublicId", uploadIntent.getPublicId()));
        outboxEventRepository.save(outboxEvent);

        uploadIntent.setConsumedAt(now);
        uploadIntentRepository.save(uploadIntent);
        return new SkinAnalysisSubmitResponse(session.getId(), session.getStatus());
    }

    @Transactional(readOnly = true)
    public SkinAnalysisResponse get(UUID analysisId) {
        User user = currentUserProvider.getUserContext();
        AnalysisSession session = sessionRepository.findByIdAndUser_Id(analysisId, user.getId())
                .orElseThrow(() -> ApiExceptionFactory.notFound("Skin analysis not found"));

        var result = resultRepository.findBySession_Id(session.getId()).orElse(null);
        var image = imageRepository.findBySession_IdAndImageView(session.getId(), AnalysisImageView.FRONT).orElse(null);
        List<SkinAnalysisResponse.Observation> observations = observationRepository
                .findBySession_IdOrderByDisplayOrderAsc(session.getId())
                .stream()
                .map(observation -> new SkinAnalysisResponse.Observation(
                        observation.getId(),
                        observation.getObservationType(),
                        observation.getSeverity(),
                        observation.getConfidence(),
                        observation.getVisibility(),
                        observation.getNote(),
                        observation.getBox2d()
                ))
                .toList();

        return new SkinAnalysisResponse(
                session.getId(),
                session.getStatus(),
                session.getCreatedAt(),
                session.getCompletedAt(),
                image == null ? null : imageStoragePort.analysisDeliveryUrl(image.getStoragePublicId()),
                result == null
                        ? null
                        : new SkinAnalysisResponse.Quality(result.isQualityAccepted(), result.getQualityIssues()),
                observations,
                result == null ? List.of() : result.getLimitations()
        );
    }

    private User lockCurrentUser() {
        User currentUser = currentUserProvider.getUserContext();
        return userRepository.findByIdForUpdate(currentUser.getId())
                .orElseThrow(() -> ApiExceptionFactory.badRequest("Current user is no longer available"));
    }

    private String analysisPublicId(User user) {
        return cloudinaryProperties.analysis().folder().strip()
                + "/" + user.getId() + "/" + UUID.randomUUID() + "/front";
    }

    private String normalizeIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return null;
        }
        String normalized = idempotencyKey.strip();
        if (normalized.length() > 255) {
            throw ApiExceptionFactory.badRequest("Idempotency-Key cannot exceed 255 characters");
        }
        return normalized;
    }
}
