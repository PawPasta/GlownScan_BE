package com.pawpasta.glowscan_be.skinanalysis.infrastructure.repository;

import com.pawpasta.glowscan_be.skinanalysis.domain.AnalysisObservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AnalysisObservationRepository extends JpaRepository<AnalysisObservation, UUID> {

    List<AnalysisObservation> findBySession_IdOrderByDisplayOrderAsc(UUID sessionId);
}
