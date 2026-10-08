package com.pawpasta.glowscan_be.cloudinary.port;

import com.pawpasta.glowscan_be.cloudinary.adapter.dto.SignedUploadIntent;
import com.pawpasta.glowscan_be.cloudinary.adapter.dto.SignedUploadRequest;

public interface ImageStoragePort {

    SignedUploadIntent createSignedUpload(SignedUploadRequest request);

    SignedUploadIntent createSignedAnalysisUpload(SignedUploadRequest request);

    String avatarDeliveryUrl(String publicId);

    String analysisDeliveryUrl(String publicId);
}
