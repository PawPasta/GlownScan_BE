package com.pawpasta.glowscan_be.skinanalysis.infrastructure.repository;

import com.pawpasta.glowscan_be.skinanalysis.domain.AnalysisImage;
import com.pawpasta.glowscan_be.skinanalysis.domain.enums.AnalysisImageView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AnalysisImageRepository extends JpaRepository<AnalysisImage, UUID> {

    Optional<AnalysisImage> findBySession_IdAndImageView(UUID sessionId, AnalysisImageView imageView);
}
