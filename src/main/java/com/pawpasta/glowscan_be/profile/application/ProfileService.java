package com.pawpasta.glowscan_be.profile.application;

import com.pawpasta.glowscan_be.auth.domain.User;
import com.pawpasta.glowscan_be.auth.infrastructure.repository.UserRepository;
import com.pawpasta.glowscan_be.cloudinary.config.CloudinaryProperties;
import com.pawpasta.glowscan_be.cloudinary.domain.CloudinaryUploadIntent;
import com.pawpasta.glowscan_be.cloudinary.infrastructure.repository.CloudinaryUploadIntentRepository;
import com.pawpasta.glowscan_be.cloudinary.adapter.dto.SignedUploadIntent;
import com.pawpasta.glowscan_be.cloudinary.adapter.dto.SignedUploadRequest;
import com.pawpasta.glowscan_be.cloudinary.port.ImageStoragePort;
import com.pawpasta.glowscan_be.profile.controller.dto.AvatarUploadIntentResponse;
import com.pawpasta.glowscan_be.profile.controller.dto.PersonalProfileResponse;
import com.pawpasta.glowscan_be.profile.controller.dto.SkinProfileResponse;
import com.pawpasta.glowscan_be.profile.controller.dto.UpdateAvatarRequest;
import com.pawpasta.glowscan_be.profile.controller.dto.UpdatePersonalProfileRequest;
import com.pawpasta.glowscan_be.profile.domain.SkinProfile;
import com.pawpasta.glowscan_be.profile.domain.UserProfile;
import com.pawpasta.glowscan_be.profile.infrastructure.SkinProfileRepository;
import com.pawpasta.glowscan_be.profile.infrastructure.UserProfileRepository;
import com.pawpasta.glowscan_be.shared.security.CurrentUserProvider;
import com.pawpasta.glowscan_be.shared.handler.ApiExceptionFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
public class ProfileService {

    private final CurrentUserProvider currentUserProvider;
    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final SkinProfileRepository skinProfileRepository;
    private final CloudinaryUploadIntentRepository uploadIntentRepository;
    private final ImageStoragePort imageStoragePort;
    private final CloudinaryProperties cloudinaryProperties;

    @Transactional
    public AvatarUploadIntentResponse createAvatarUploadIntent() {
        User user = lockCurrentUser();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime expiresAt = now.plus(cloudinaryProperties.avatar().uploadIntentTtl());

        uploadIntentRepository.revokePendingByUserId(user.getId(), now);

        CloudinaryUploadIntent uploadIntent = new CloudinaryUploadIntent();
        uploadIntent.setUser(user);
        uploadIntent.setPublicId(avatarPublicId(user));
        uploadIntent.setExpiresAt(expiresAt);
        uploadIntent = uploadIntentRepository.saveAndFlush(uploadIntent);

        SignedUploadIntent signedUpload = imageStoragePort.createSignedUpload(
                new SignedUploadRequest(uploadIntent.getPublicId(), expiresAt)
        );
        return new AvatarUploadIntentResponse(
                uploadIntent.getId(),
                signedUpload.uploadUrl(),
                signedUpload.cloudName(),
                signedUpload.apiKey(),
                signedUpload.timestamp(),
                signedUpload.signature(),
                signedUpload.publicId(),
                signedUpload.overwrite(),
                signedUpload.expiresAt(),
                new AvatarUploadIntentResponse.AvatarUploadConstraints(
                        signedUpload.allowedFormats(),
                        cloudinaryProperties.avatar().maxBytes(),
                        cloudinaryProperties.avatar().maxDimension()
                )
        );
    }

    @Transactional
    public String confirmAvatar(UpdateAvatarRequest request) {
        User user = lockCurrentUser();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        CloudinaryUploadIntent uploadIntent = uploadIntentRepository
                .findByIdAndUserIdForUpdate(request.uploadIntentId(), user.getId())
                .orElseThrow(() -> ApiExceptionFactory.badRequest("Avatar upload intent is invalid or expired"));

        if (uploadIntent.getConsumedAt() != null
                || uploadIntent.getRevokedAt() != null
                || !uploadIntent.getExpiresAt().isAfter(now)) {
            throw ApiExceptionFactory.badRequest("Avatar upload intent is invalid or expired");
        }

        UserProfile userProfile = userProfileRepository.findByUserId(user.getId())
                .orElseGet(() -> createUserProfile(user));
        userProfile.setAvatarUrl(imageStoragePort.avatarDeliveryUrl(uploadIntent.getPublicId()));
        userProfileRepository.save(userProfile);

        uploadIntent.setConsumedAt(now);
        uploadIntentRepository.save(uploadIntent);
        return "Avatar updated successfully";
    }

    @Transactional(readOnly = true)
    public PersonalProfileResponse getCurrentPersonalProfile() {
        User user = currentUserProvider.getUserContext();
        UserProfile userProfile = userProfileRepository.findByUserId(user.getId())
                .orElseThrow(() -> ApiExceptionFactory.notFound("Personal profile not found"));

        return toPersonalProfileResponse(userProfile);
    }

    @Transactional
    public PersonalProfileResponse updateCurrentPersonalProfile(UpdatePersonalProfileRequest request) {
        if (!request.hasUpdates()) {
            throw ApiExceptionFactory.badRequest("At least one profile field must be provided");
        }

        User user = lockCurrentUser();
        UserProfile userProfile = userProfileRepository.findByUserId(user.getId())
                .orElseGet(() -> createUserProfile(user));

        if (request.hasFullName()) {
            userProfile.setFullName(request.getFullName() == null ? null : request.getFullName().strip());
        }
        if (request.hasDateOfBirth()) {
            userProfile.setDateOfBirth(request.getDateOfBirth());
        }
        if (request.hasGender()) {
            userProfile.setGender(request.getGender());
        }

        return toPersonalProfileResponse(userProfileRepository.saveAndFlush(userProfile));
    }

    @Transactional(readOnly = true)
    public SkinProfileResponse getCurrentSkinProfile() {
        User user = currentUserProvider.getUserContext();
        SkinProfile skinProfile = skinProfileRepository.findByUserProfile_User_Id(user.getId())
                .orElseThrow(() -> ApiExceptionFactory.notFound("Skin profile not found"));

        return new SkinProfileResponse(
                skinProfile.getId(),
                skinProfile.getSkinType(),
                skinProfile.getSensitivityLevel(),
                skinProfile.getNotes(),
                skinProfile.getCreatedAt(),
                skinProfile.getUpdatedAt()
        );
    }

    private String avatarPublicId(User user) {
        return cloudinaryProperties.avatar().folder().strip() + "/" + user.getId();
    }

    private UserProfile createUserProfile(User user) {
        UserProfile profile = new UserProfile();
        profile.setUser(user);
        return profile;
    }

    private PersonalProfileResponse toPersonalProfileResponse(UserProfile userProfile) {
        return new PersonalProfileResponse(
                userProfile.getId(),
                userProfile.getFullName(),
                userProfile.getAvatarUrl(),
                userProfile.getDateOfBirth(),
                userProfile.getGender(),
                userProfile.getCreatedAt(),
                userProfile.getUpdatedAt()
        );
    }

    private User lockCurrentUser() {
        User currentUser = currentUserProvider.getUserContext();
        return userRepository.findByIdForUpdate(currentUser.getId())
                .orElseThrow(() -> ApiExceptionFactory.badRequest("Current user is no longer available"));
    }
}
