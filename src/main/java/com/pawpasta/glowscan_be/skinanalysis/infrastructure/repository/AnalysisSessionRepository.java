package com.pawpasta.glowscan_be.skinanalysis.infrastructure.repository;

import com.pawpasta.glowscan_be.skinanalysis.domain.AnalysisSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AnalysisSessionRepository extends JpaRepository<AnalysisSession, UUID> {

    Optional<AnalysisSession> findByIdAndUser_Id(UUID id, UUID userId);

    Optional<AnalysisSession> findByUser_IdAndIdempotencyKey(UUID userId, String idempotencyKey);
}
