package com.pawpasta.glowscan_be.cloudinary.port;

import com.pawpasta.glowscan_be.cloudinary.model.SignedUploadIntent;
import com.pawpasta.glowscan_be.cloudinary.model.SignedUploadRequest;

public interface ImageStoragePort {

    SignedUploadIntent createSignedUpload(SignedUploadRequest request);

    String avatarDeliveryUrl(String publicId);
}
